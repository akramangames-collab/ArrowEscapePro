#!/usr/bin/env python3
"""Recover verified Android build tools and sign using a separate private key folder.

Credentials are read only into subprocess environments, never printed or copied
into source/artifacts. No publication or GitHub action is performed here.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from urllib.parse import urlparse
import argparse,hashlib,json,os,subprocess,tarfile,zipfile,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
CACHE=ROOT.parent/'android-build-cache'

def fetch(url,path,expected=None,algorithm='sha256'):
    path.parent.mkdir(parents=True,exist_ok=True)
    if expected and path.exists() and hashlib.new(algorithm,path.read_bytes()).hexdigest()==expected:return
    for attempt in range(2):
        temp=path.with_suffix(path.suffix+'.download')
        subprocess.run(['curl','--http1.1','-fsSL','--retry','2','--connect-timeout','20','--max-time','240',
            '-o',str(temp),url],check=True)
        if expected and hashlib.new(algorithm,temp.read_bytes()).hexdigest()!=expected:
            temp.unlink();continue
        temp.replace(path);return
    raise RuntimeError('Official download checksum did not match')

def sdk_package(repo,path):
    package=next(p for p in repo.findall('remotePackage') if p.attrib['path']==path)
    archive=next(a for a in package.findall('./archives/archive') if a.findtext('host-os') in [None,'linux'])
    zip_path=CACHE/(path.replace(';','-')+'.zip')
    fetch('https://dl.google.com/android/repository/'+archive.findtext('./complete/url'),zip_path,
          archive.findtext('./complete/checksum'),'sha1')
    dest=CACHE/'sdk'/path.replace(';','/')
    with zipfile.ZipFile(zip_path) as z:
        for info in z.infolist():
            parts=Path(info.filename).parts[1:]
            if not parts:continue
            target=dest.joinpath(*parts)
            if info.is_dir():target.mkdir(parents=True,exist_ok=True)
            else:
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(info))
                if (info.external_attr>>16)&0o111:target.chmod(0o755)
    local=ET.Element('localPackage',{'path':path,'obsolete':'false'})
    for child in package:
        if child.tag in ['type-details','revision','display-name','uses-license']:local.append(deepcopy(child))
    body=ET.tostring(repo.find('license'),encoding='unicode')+ET.tostring(local,encoding='unicode')
    (dest/'package.xml').write_text('<?xml version="1.0" encoding="UTF-8"?><common:repository '
        'xmlns:common="http://schemas.android.com/repository/android/common/02" '
        'xmlns:sdk="http://schemas.android.com/sdk/android/repo/repository2/03" '
        'xmlns:generic="http://schemas.android.com/repository/android/generic/02">'+body+'</common:repository>')
    print('Verified/restored SDK:',path,flush=True)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--signing-dir',type=Path)
    parser.add_argument('tasks',nargs='*');args=parser.parse_args()
    CACHE.mkdir(exist_ok=True)
    fetch('https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256',CACHE/'gradle.sha256')
    fetch('https://services.gradle.org/distributions/gradle-9.6.0-bin.zip',CACHE/'gradle.zip',(CACHE/'gradle.sha256').read_text().strip())
    if not (CACHE/'gradle/gradle-9.6.0/bin/gradle').exists():
        with zipfile.ZipFile(CACHE/'gradle.zip') as z:z.extractall(CACHE/'gradle')
        (CACHE/'gradle/gradle-9.6.0/bin/gradle').chmod(0o755)
    fetch('https://dl.google.com/android/repository/repository2-3.xml',CACHE/'repository.xml')
    repo=ET.parse(CACHE/'repository.xml').getroot()
    fetch('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse',CACHE/'jdk-metadata.json')
    pkg=json.loads((CACHE/'jdk-metadata.json').read_text())[0]['binary']['package']
    with ThreadPoolExecutor(max_workers=4) as pool:
        jobs=[pool.submit(sdk_package,repo,p) for p in ['platforms;android-36.1','build-tools;36.0.0','platform-tools']]
        jobs.append(pool.submit(fetch,pkg['link'],CACHE/'jdk.tar.gz',pkg['checksum']))
        for job in jobs:job.result()
    if not (CACHE/'jdk').exists():
        (CACHE/'jdk').mkdir()
        with tarfile.open(CACHE/'jdk.tar.gz') as z:z.extractall(CACHE/'jdk',filter='data')
    jdk=next(p for p in (CACHE/'jdk').iterdir() if p.is_dir() and (p/'bin/javac').exists())
    env=os.environ.copy();env.update(JAVA_HOME=str(jdk),PATH=str(jdk/"bin")+os.pathsep+env.get("PATH",""),ANDROID_HOME=str(CACHE/"sdk"),ANDROID_SDK_ROOT=str(CACHE/"sdk"),GRADLE_USER_HOME=str(CACHE/"gradle-user-home"))
    if args.signing_dir:
        fields={}
        for line in (args.signing_dir/'UPLOAD-KEY-CREDENTIALS.txt').read_text().splitlines():
            if ':' in line:
                key,value=line.split(':',1);fields[key.strip()]=value.strip()
        env=os.environ.copy();env.update(JAVA_HOME=str(jdk),PATH=str(jdk/'bin')+os.pathsep+env.get('PATH',''),
            ANDROID_HOME=str(CACHE/'sdk'),ANDROID_SDK_ROOT=str(CACHE/'sdk'),GRADLE_USER_HOME=str(CACHE/'gradle-user-home'),
            ARROW_RELEASE_STORE_FILE=str(args.signing_dir/'arrowescape-upload-key.jks'),
            ARROW_RELEASE_STORE_PASSWORD=fields['Store password'],ARROW_RELEASE_KEY_PASSWORD=fields['Key password'],ARROW_RELEASE_KEY_ALIAS=fields['Alias'])
        cert=subprocess.run([str(jdk/'bin/keytool'),'-list','-v','-keystore',env['ARROW_RELEASE_STORE_FILE'],
            '-storepass:env','ARROW_RELEASE_STORE_PASSWORD','-alias',env['ARROW_RELEASE_KEY_ALIAS']],env=env,capture_output=True,text=True,check=True)
        fingerprint=next(l.split('SHA256:',1)[1].strip().replace(':','').lower() for l in cert.stdout.splitlines() if 'SHA256:' in l)
        assert fingerprint=='c0f5b84b375b4775bcc67c37e057a26cb3d654fe7938d48b658eb423e441429f'
        print('Original signing certificate verified.',flush=True)
    options=['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
    proxy=urlparse(env.get('HTTPS_PROXY',''))
    if proxy.hostname:
        options+=[f'-D{kind}.proxy{field}={value}' for kind in ['http','https'] for field,value in [('Host',proxy.hostname),('Port',proxy.port or 80)]]
    init=CACHE/'test-network.gradle'
    init.write_text("allprojects { tasks.withType(Test).configureEach { systemProperty 'robolectric.dependency.repo.url','https://repo.maven.apache.org/maven2'; for(String n:['https.proxyHost','https.proxyPort','http.proxyHost','http.proxyPort','javax.net.ssl.trustStore']) { String v=System.getProperty(n); if(v!=null) systemProperty n,v } } }")
    tasks=args.tasks or ['testDebugUnitTest','assembleRelease','bundleRelease','lintRelease']
    command=[str(CACHE/'gradle/gradle-9.6.0/bin/gradle'),'--no-daemon','--console=plain','--max-workers=4',
        '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8','--init-script',str(init)]+options+tasks
    print('Building and testing; log:',str(CACHE/'build.log'),flush=True)
    with (CACHE/'build.log').open('w') as log:result=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    print('Gradle exit:',result.returncode,flush=True)
    raise SystemExit(result.returncode)

if __name__=='__main__':main()
