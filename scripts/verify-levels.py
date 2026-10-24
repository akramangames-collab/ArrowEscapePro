#!/usr/bin/env python3
"""Compatibility entry point for the current campaign."""
import runpy
from pathlib import Path
runpy.run_path(str(Path(__file__).with_name('verify-water-levels.py')),run_name="__main__")
