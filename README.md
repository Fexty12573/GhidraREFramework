# Usage

## Prerequisites
* A memory dump of MHR
  * Open MHR
  * Open [x64dbg](https://x64dbg.com/).
  * In x64dbg, navigate to Plugins -> Scylla
  * Select MHR from the process dropdown
  * Click on "IAT Autosearch"
  * Click "Dump"
* **At least** 16GB of RAM. Preferrably 32.
* Recommended: Close all resource hungry programs that take a lot of RAM and use your CPU a lot. 

## Running the script
1. Use REFramework to dump the SDK
2. Compactify the JSON dump. You can use the `jsoncompactor.py` script provided in this repo or whatever way you prefer.
3. Navigate to your ghidra installation directory. Right-click -> Edit on the `ghidraRun.bat` file.
4. Remove the `::` from `::set MAXMEM=2G`, and change `2G` to **at least** `6G`. That is the bare minimum. The speed of the script depends on the amount of memory you give ghidra. 6GB is a bare minimum and the script will run very slow with it. I recommend allocating at least 9GB. If you have 32GB of RAM I recommend giving it 16GB. That way it will run without issues.
    * Note that, **more RAM =/= faster**, but **less RAM == slower**.
    * Restart ghidra after doing this.
5. Download the latest org.json jar from here: https://github.com/stleary/JSON-java (Download is at the top of the readme) and put it into a folder that you can access later.
6. Open your MHR project, ideally with a freshly imported, un-analyzed, MHR binary.
7. In the ghidra project manager, click on `Edit -> Plugin Path`. Then click "Add Jar" and select the jar you downloaded earlier.
8. Open your MHR binary in the code browser, click yes if it prompts you to auto-analyze. (If not, you can open the window via `Analysis -> Auto Analyze...`)
9.  In the Auto-Analysis options page, **deselect all options**, then click "Apply", and then "Analyze".
10. Open the script manager in ghidra.
11. Click on the 3 horizontal lines icon in the top right of the window.
12. Click on the Green **+** icon and select the `GhidraMHRise` directory.
13. Find the IL2CPPDumpImporter script in the list and run it. Make sure you select the **compact** JSON dump when asked.
    * I recommend leaving the import filter set to `snow`/`app`/`via`/`System`. *If* you gave ghidra enough RAM (8GB+), the script should finish in approximately 10-20 minutes.
    * Also note that, the way the filter works, is that it will import every class that starts with the filter, *and* it will import as many classes as needed to fully construct those types, recursively.

## Post Import
Depending on if you decided to run the post-import disassemble script or not, you might want to also let ghidra analyze the binary. If you do then you can use these options:
- ASCII Strings
- Create Address Tables
- Data Reference
- Function Start Search
- Reference
- Shared Return Calls
- Stack
- Subroutine References
- Windows x86 PE Exception Handling

The analysis should not take very long with these options.

# Credits
* Stracker
  * Various improvements and additions
  * Making the script much faster
* Me :)
