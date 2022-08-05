import ghidra.app.script.GhidraScript;

// Run after importing an il2cpp dump and enabling auto analysis again
// Disassembles all functions that have not been disassembled
public class PostImportDisassemble extends GhidraScript {
    @Override
    protected void run() throws Exception {
        for (var function : currentProgram.getFunctionManager().getFunctions(true)) {
            if (!function.getBody().contains(function.getEntryPoint().add(1))) {
                disassemble(function.getEntryPoint());
            }
        }
    }

}
