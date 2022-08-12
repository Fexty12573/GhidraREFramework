//Imports SDK dumps generated by REFramework and uses it to annotate the program.
//@author Fexty
//@category Annotation
//@keybinding
//@menupath
//@toolbar

import ghidra.app.script.GhidraScript;
import ghidra.app.services.DataTypeManagerService;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressFactory;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.Namespace;
import ghidra.program.model.symbol.SourceType;
import ghidra.program.model.symbol.SymbolTable;

import org.json.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IL2CPPDumpImporter extends GhidraScript {

	// private static final String CLASS_FILTER = "snow.data.Dango"; // Set to null
	// or "" for no filter.

	static public FunctionManager functionManager;
	static public DataTypeManager typeManager;
	static public DataTypeManager builtinTypeManager;
	static public AddressFactory addressFactory;
	static public SymbolTable symbolTable;
	static public CategoryPath category = new CategoryPath("/IL2CPP_Types");
	static public HashMap<String, DataType> valueTypes;

	static public HashMap<String, REField[]> fixedNativeFields = new HashMap<String, REField[]>();

	private int classesAdded;
	private int classesToAdd;
	private String classFilter;

	private JSONObject il2cppDump;
	private HashMap<String, RETypeDefinition> typeMap;

	@Override
	protected void run() throws Exception {
		initialize();
		importIL2CPPDump();
	}

	private void initialize() throws Exception {
		functionManager = currentProgram.getFunctionManager();
		builtinTypeManager = state.getTool().getService(DataTypeManagerService.class).getBuiltInDataTypesManager();
		addressFactory = currentProgram.getAddressFactory();
		symbolTable = currentProgram.getSymbolTable();
		typeMap = new HashMap<>();

		typeManager = currentProgram.getDataTypeManager();

		if (typeManager == null) {
			throw new Exception("failed to find typemanager");
		}

		final var uint8_t = typeManager.addDataType(
				new TypedefDataType("uint8_t", builtinTypeManager.getDataType("/uchar")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var int8_t = typeManager.addDataType(
				new TypedefDataType("int8_t", builtinTypeManager.getDataType("/char")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var uint16_t = typeManager.addDataType(
				new TypedefDataType("uint16_t", builtinTypeManager.getDataType("/ushort")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var int16_t = typeManager.addDataType(
				new TypedefDataType("int16_t", builtinTypeManager.getDataType("/short")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var uint32_t = typeManager.addDataType(
				new TypedefDataType("uint32_t", builtinTypeManager.getDataType("/uint")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var int32_t = typeManager.addDataType(
				new TypedefDataType("int32_t", builtinTypeManager.getDataType("/int")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var uint64_t = typeManager.addDataType(
				new TypedefDataType("uint64_t", builtinTypeManager.getDataType("/ulonglong")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var int64_t = typeManager.addDataType(
				new TypedefDataType("int64_t", builtinTypeManager.getDataType("/longlong")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var uintptr_t = typeManager.addDataType(
				new TypedefDataType("uintptr_t", builtinTypeManager.getDataType("/ulonglong")),
				DataTypeConflictHandler.REPLACE_HANDLER);
		final var intptr_t = typeManager.addDataType(
				new TypedefDataType("intptr_t", builtinTypeManager.getDataType("/ulonglong")),
				DataTypeConflictHandler.REPLACE_HANDLER);

		typeManager.addDataType(builtinTypeManager.getDataType("/void"), DataTypeConflictHandler.DEFAULT_HANDLER);
		typeManager.addDataType(new PointerDataType(typeManager.getDataType("/void")),
				DataTypeConflictHandler.DEFAULT_HANDLER);

		valueTypes = new HashMap<>();
		valueTypes.put("System.Single", builtinTypeManager.getDataType("/float"));
		valueTypes.put("System.Double", builtinTypeManager.getDataType("/double"));
		valueTypes.put("System.Void", builtinTypeManager.getDataType("/void"));
		valueTypes.put("System.UInt8", uint8_t);
		valueTypes.put("System.UInt16", uint16_t);
		valueTypes.put("System.UInt32", uint32_t);
		valueTypes.put("System.UInt64", uint64_t);
		valueTypes.put("System.Int8", int8_t);
		valueTypes.put("System.Int16", int16_t);
		valueTypes.put("System.Int32", int32_t);
		valueTypes.put("System.Int64", int64_t);
		valueTypes.put("System.SByte", builtinTypeManager.getDataType("/byte"));
		valueTypes.put("System.Byte", builtinTypeManager.getDataType("/byte"));
		valueTypes.put("System.UByte", builtinTypeManager.getDataType("/uchar"));
		valueTypes.put("System.UIntPtr", uintptr_t);
		valueTypes.put("System.IntPtr", intptr_t);
		valueTypes.put("System.Char", builtinTypeManager.getDataType("/char"));
		valueTypes.put("System.UChar", builtinTypeManager.getDataType("/uchar"));
		valueTypes.put("System.Void*", typeManager.getDataType("/void *"));
		valueTypes.put("System.Boolean", builtinTypeManager.getDataType("/bool"));
		valueTypes.put("System.TypeCode", builtinTypeManager.getDataType("/int"));
		valueTypes.put("System.DateTime", uint64_t);
		valueTypes.put("System.TimeSpan", int64_t);
		valueTypes.put("s8", int8_t);
		valueTypes.put("u8", uint8_t);
		valueTypes.put("s16", int16_t);
		valueTypes.put("u16", uint16_t);
		valueTypes.put("s32", int32_t);
		valueTypes.put("u32", uint32_t);
		valueTypes.put("s64", int64_t);
		valueTypes.put("u64", uint64_t);
		valueTypes.put("via.Color", uint32_t);

		// TODO: Handle geometric engine types like via.vec4 or via.mat4.
		// They could be represented as simple float arrays or by explicitly creating a
		// type for the beforehand.

		// Fixed known definitions
		fixedNativeFields.put("System.Object", new REField[] {
				new REField("info", "System.Void*", 0x0),
				new REField("referenceCount", "System.UInt32", 0x8),
				new REField("N000071AE", "System.Int16", 0xc),
		});

		fixedNativeFields.put("via.Component", new REField[] {
				new REField("ownerGameObject", "via.GameObject", 0x10),
				new REField("childComponent", "via.Component", 0x18),
				new REField("prevComponent", "via.Component", 0x20),
				new REField("nextComponent", "via.Component", 0x28),
		});

		fixedNativeFields.put("via.GameObject", new REField[] {
				new REField("transform", "via.Transform", 0x18),

		});
	}

	private void importIL2CPPDump() throws Exception {
		boolean runDisassemble = false;

		if (!askYesNo("Close archive",
				"Do not forget to collapse the exe type archive or ghidra will freeze during a large import\nContinue ?")) {
			return;
		}

		if (askYesNo("Auto-Disassemble",
				"Do you want to automatically run the post import disassemble script after importing?")) {
			runDisassemble = true;
		}

		File file = askFile("Select IL2CPP Dump", "Open");
		il2cppDump = new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));

		println("JSON Loaded");

		int count = il2cppDump.length();
		int i = 0;
		for (var key : il2cppDump.keySet()) {
			typeMap.put(key, new RETypeDefinition(key, il2cppDump.getJSONObject(key)));
			println(String.format("parsed (%d/%d)", i++, count));
		}
		println("JSON parsed");
		il2cppDump.clear();
		System.gc();

		classFilter = askString("Filter", "Select Class Filter", "snow");

		var keys = typeMap.keySet();
		if (classFilter == null || classFilter.isEmpty()) {
			classesToAdd = keys.size();

			// Add all types + Methods to ghidra
			for (var key : keys) {
				parseClass(key);
			}
		} else {
			Set<String> filtered = keys
					.stream()
					.filter((name) -> name.startsWith(classFilter))
					.collect(Collectors.toSet());
			classesToAdd = filtered.size();

			for (var key : filtered) {
				parseClass(key);
			}
		}
		System.gc();

		if (runDisassemble) {
			runScript("PostImportDisassemble.java", state);
		}
	}

	public boolean isValueType(String name) {
		return valueTypes.containsKey(name);
	}

	public DataType getValueType(String objectName) {
		return valueTypes.get(objectName);
	}

	private DataType getValueTypeOrType(String name) {
		// Top-level types start with a '/', should only occur for built-in types.
		if (name.charAt(0) == '/') {
			var dt = typeManager.getDataType(name);
			if (dt == null) {
				println("Failed to find datatype:" + name);
			}
			return dt;
		}

		if (valueTypes.containsKey(name)) {
			return valueTypes.get(name);
		}

		// Type is not a ValueType
		RETypeDefinition type = typeMap.getOrDefault(name, null);
		if (type != null) {
			parseClass(name);
			var dt = typeMap.get(name).dataType;
			if (dt == null) {
				// Should only happen in case an Exception is thrown in parseClass
				println("dt is null for " + name + " after parsing");
			}

			return dt;
		}

		return null;
	}

	private DataType getPassingType(String name) {
		// We need to handle Enums, ValueTypes, and all other types separately.
		// Enums are the only non-ValueTypes are usually passed by value and stored in
		// their value form too.
		// ValueTypes are stored in value form so we use "built-in" ghidra types to
		// simplify things.
		// All other types are stored on the heap and are only accessed via pointers.
		DataType type = getValueTypeOrType(name);
		if (type == null) {
			return null;
		}
		if (isValueType(name) && type.getLength() <= 8) {
			return type;
		}

		var typedef = typeMap.get(name);
		if (typedef == null || typedef.isEnum) {
			return type;
		}
		return typedef.pointerTo;
	}

	private Namespace getOrCreateNamespace(String name) {
		// Packed into a function because I hate java's enforced exception handling or
		// continued propagating.
		try {
			return symbolTable.getOrCreateNameSpace(currentProgram.getGlobalNamespace(), name, SourceType.IMPORTED);
		} catch (Exception e) {
			println("error getOrCreateNamespace:" + e.getMessage());
			return null;
		}
	}

	private void parseClass(String name) {
		if (!typeMap.containsKey(name)) {
			return;
		}

		RETypeDefinition definition = typeMap.get(name);
		if (definition.dataType != null) {
			return;
		}

		// Not actually an accurate display of progress if a filter is used but at least
		// gives me an idea of how much was already completed lol.
		println(String.format("(%d/%d) Parsing class %s", classesAdded, classesToAdd, name));
		classesAdded++;
		if (!name.startsWith(classFilter)) {
			classesToAdd++;
		}

		// Create ghidra type from type definition
		DataType type = new StructureDataType(name, definition.size);

		// Enum types should be added as an actual enum, not a structure. They DO have a
		// structure representation however it is only very rarely used so I don't see
		// the point of adding that.
		// Also, this 'if' is inside the hasParent 'if' because enums always have
		// System.Enum as parent
		if (definition.isEnum) {
			var enumType = new EnumDataType(name, getValueType(definition.underlyingType).getLength());

			for (var field : definition.fields) {
				try {
					enumType.add(field.name, field.defaultValue);
				} catch (IllegalArgumentException e) {
					println(e.getMessage());
					println(e.getStackTrace()[0].toString());
				}

			}

			definition.size = enumType.getLength();

			// Overwrite ghidra type of type definition
			type = enumType;
		}

		// Register in archive before doing any new recursive parsing
		definition.dataType = typeManager.addDataType(type, DataTypeConflictHandler.REPLACE_HANDLER);
		definition.pointerTo = typeManager.addDataType(new PointerDataType(definition.dataType),
				DataTypeConflictHandler.REPLACE_HANDLER);

		// Parse parent class before parsing current class
		if (definition.hasParent()) {
			parseClass(definition.parent);
		}

		// Add all fields to the class
		if (definition.dataType instanceof Structure) {
			if (definition.name.endsWith("[]")) {
				addFieldsToArrayType(definition, (Structure) definition.dataType);
			} else {
				addFieldsOfClassToType(definition, (Structure) definition.dataType);
			}
		}

		// Add all methods
		if (!definition.methods.isEmpty()) {
			for (var method : definition.methods) {
				parseMethod(method, definition);
			}
		}

		println(String.format("parsing %s done", name));
	}

	private void handleStaticGetter(RETypeDefinition parent, REMethod method) {
		try {
			var fieldName = method.name.substring(4);
			var fieldType = getPassingType(method.returnType);

			disassemble(toAddr(method.address));
			var instruction = getInstructionAt(toAddr(method.address));
			if (instruction == null) {
				return;
			}

			if (!instruction.getMnemonicString().equals("MOV")) {
				return;
			}

			if (!instruction.getNext().getMnemonicString().equals("RET")) {
				return;
			}

			var addr = (Address) instruction.getOpObjects(1)[0];
			if (addr == null) {
				return;
			}

			createLabel(addr, fieldName, getOrCreateNamespace(parent.name), false, SourceType.IMPORTED);
			createData(addr, fieldType);
		} catch (Exception e) {
			printf("Failed to parse static getter:  %s", e.getMessage());
			println();
		}
	}

	private void parseMethod(REMethod method, RETypeDefinition parent) {
		if (method.address == 0) {
			return;
		}

		if (method.flags.contains("Static") && method.name.startsWith("get_")) {
			handleStaticGetter(parent, method);
		}

		// If there are already symbols here, this is a generic function and we don't
		// actually want to have a typed function, remove the function if it's there and
		// add labels and a generic function to mark it's been acknowledged
		var address = addressFactory.getAddress(method.addressString);
		var symbol = getSymbolAt(address);
		if (symbol != null) {
			try {
				Function existing = functionManager.getFunctionAt(address);
				if (existing != null && existing.getParentNamespace() != currentProgram.getGlobalNamespace()) {
					createLabel(address, existing.getName(), existing.getParentNamespace(), false,
							SourceType.USER_DEFINED);
					functionManager.removeFunction(address);
					var name = String.format("GenericFunction_%x", address.getOffset());
					if (existing.getName().equals(method.name)) {
						name = method.name;
					}
					createFunction(address, name);
				}

				createLabel(address, method.name, getOrCreateNamespace(parent.name), false,
						SourceType.USER_DEFINED);
			} catch (Exception e) {
				println("error creating label: " + e.getMessage());
			}
			return;
		}

		Function function;

		// If the function does not yet exist then we try to create it and then rename
		// it. I don't know how this
		// behaves if the bytes at this location have not yet been disassembled.
		try {
			function = createFunction(address, method.name);
			function.setParentNamespace(getOrCreateNamespace(parent.name));
			// That could be useful, but it's not worth the huge slowdown it causes
			// function.setComment(String.format("flags: %s\nimpl flags: %s", method.flags,
			// method.implFlags));
		} catch (Exception e) {
			println("error creating function: " + e.getMessage());
			return;
		}

		// Add all parameters to the function. Code is not complete because there are a
		// million exceptions to consider where certain parameters do not exist or
		// others exist even tho the dump does not specify them.
		// As a general rule tho:
		// - The engine conforms to the x64 calling convention so parameters are RCX,
		// RDX, R8, R9, Stack...
		// - (Basically) all methods take a thread context as their first argument in
		// RCX (or RDX, exception below)
		// - If a method has the 'HasThis' flag, then it takes a 'this' parameter in RDX
		// (or R8, ...)
		// - All parameters listed by the dump follow after these 2 in R8/R9 and then on
		// the stack starting at 0x28
		// - The size of an argument on the stack is at most 8 bytes. Types with size
		// greater 8 are passed as a
		// pointer.
		// - Stack arguments are always 8 byte aligned regardless of the size of the
		// type. So +0x28, +0x30, +0x38...
		// - If a function has a return type which is a ValueType and its size is
		// greater than sizeof(void*) then
		// this parameter is passed as a pointer in RCX *always*. It is also returned as
		// a pointer
		// I believe there are also some types that aren't explicitly ValueTypes but
		// still adhere to this behavior.
		// - There might be some more intricacies that I have missed...
		var params = method.parameters;
		var funcParams = new ArrayList<ParameterImpl>();

		try {
			// Get the return type
			var retType = getPassingType(method.returnType);

			var ret = new ReturnParameterImpl(retType, currentProgram);

			// Methods always take the thread context as their first parameter
			funcParams.add(new ParameterImpl("vmctx", getValueTypeOrType("/void *"), currentProgram));

			if (method.implFlags.contains("HasThis")) {
				funcParams
						.add(new ParameterImpl("this", parent.dataType, currentProgram));
			}

			for (var param : params) {
				funcParams.add(new ParameterImpl(param.name, getValueTypeOrType(param.type), currentProgram));
			}

			// Using this function as Function.addParameter is deprecated. This also makes
			// things easier as
			// ghidra tries to determine Register and stack offset by itself.
			function.updateFunction("__fastcall", ret, funcParams,
					Function.FunctionUpdateType.DYNAMIC_STORAGE_ALL_PARAMS, true, SourceType.IMPORTED);
		} catch (Exception e) {
			println("error parsing function signature:" + e.getMessage());
		}
	}

	private void addFieldsToArrayType(RETypeDefinition definition, Structure type) {
		type.deleteAll();
		type.growStructure(0x20);

		type.replaceAtOffset(0x0, getValueTypeOrType("/void *"), 8, "object_info", "");
		type.replaceAtOffset(0x8, getValueType("System.UInt32"), 4, "ref_count", "");
		type.replaceAtOffset(0x10, getValueTypeOrType("/void *"), 8, "contained_type", "");
		type.replaceAtOffset(0x18, getValueType("System.UInt32"), 4, "_n", "");
		type.replaceAtOffset(0x1C, getValueType("System.UInt32"), 4, "Count", "");

		String containedType = definition.name.replace("[]", "");
		var containedDataType = getPassingType(containedType);
		if (containedDataType == null) {
			containedDataType = getValueTypeOrType("/void *");
		}
		type.growStructure(containedDataType.getLength());
		type.replaceAtOffset(0x20,
				new ArrayDataType(containedDataType, 1, containedDataType.getLength()),
				containedDataType.getLength(), "Elements", "");
	}

	private void addFieldsOfClassToType(RETypeDefinition definition, Structure type) {
		if (definition == null) {
			return;
		}

		if (definition.size == 0) {
			return;
		}
		type.setDescription(String.format("%s:0x%x -> ", definition.name, definition.size) + type.getDescription());

		try {
			if (fixedNativeFields.containsKey(definition.name)) {
				addFieldsToType(Arrays.asList(fixedNativeFields.get(definition.name)), type);
			}
			if (definition.hasFields()) {
				addFieldsToType(definition.fields, type);
			}
			if (definition.hasParent()) {
				addFieldsOfClassToType(typeMap.get(definition.parent), type);
			}
		} catch (Exception e) {
			println("Exception adding fields for " + definition.name + " :" +
					e.toString());
		}
	}

	private void addFieldsToType(List<REField> fields, Structure type) throws Exception {
		for (var field : fields) {
			if (!field.isStatic()) {

				var fieldDataType = getPassingType(field.type);
				if (fieldDataType == null) {
					continue;
				}
				type.replaceAtOffset(field.offsetFromBase, fieldDataType, fieldDataType.getLength(), field.name,
						field.flags);
			}
		}
	}

	private static class REMethod {
		public static class Parameter {
			public String name;
			public String type;

			public Parameter(String n, String t) {
				name = n;
				type = t;
			}
		}

		public String name;
		public String flags;
		public long address;
		public String addressString;
		public int id;
		public int invokeId;
		public String implFlags;
		public ArrayList<Parameter> parameters;
		public String returnType;

		REMethod(String name, JSONObject method) {
			flags = method.has("flags") ? method.getString("flags") : "";
			addressString = method.getString("function");
			address = Long.parseLong(addressString, 16);
			id = method.getInt("id");
			invokeId = method.getInt("invoke_id");
			if (method.has("impl_flags")) {
				implFlags = method.getString("impl_flags");
			} else {
				implFlags = "";
			}

			// Since method names are stored with [Name][ID] we remove the ID to avoid
			// confusing names in ghidra.
			// Also constructor names always start with a '.' so we remove that also to
			// avoid 'Namespace::.Name'
			this.name = (name.startsWith(".") ? name.substring(1) : name).substring(0,
					name.indexOf(Integer.toString(id)));

			parameters = new ArrayList<>();
			if (method.has("params")) {
				var params = method.getJSONArray("params");
				for (int i = 0; i < params.length(); i++) {
					var param = params.getJSONObject(i);
					parameters.add(new Parameter(param.getString("name"), param.getString("type")));
				}
			}

			returnType = method.getJSONObject("returns").getString("type");
		}
	}

	private static class REField {
		public String flags;
		public int id;
		public int offsetFromBase;
		public int offsetFromFieldPtr;
		public String type;
		public String name;
		public int defaultValue;

		public REField(String name, String type, int offset) {
			this.name = name;
			this.type = type;
			offsetFromBase = offset;
			flags = "";
		}

		public REField(String name, JSONObject field) {
			flags = field.has("flags") ? field.getString("flags") : "";
			id = field.getInt("id");
			offsetFromBase = Integer.parseInt(field.getString("offset_from_base").substring(2), 16);

			// Only ever used for ValueTypes, can ignore for the most part. Kept it here for
			// completeness' sake.
			offsetFromFieldPtr = Integer.parseInt(field.getString("offset_from_fieldptr").substring(2), 16);
			type = field.getString("type");

			// Only really used for enum members
			defaultValue = field.optInt("default", 0);

			this.name = name;
			if (this.name.matches("<(.+)>k__BackingField")) {
				this.name = "__" + this.name.substring(1, this.name.length() - 16);
				this.flags += " | BackingField";
			}
		}

		public boolean isStatic() {
			return flags.contains("Static");
		}
	}

	private static class RETypeDefinition {
		public String name;
		public int size;
		public String parent;
		public ArrayList<REField> fields;
		public ArrayList<REMethod> methods;
		public boolean isValueType;
		public boolean isEnum;
		public String underlyingType;

		public DataType dataType;
		public DataType pointerTo;

		public RETypeDefinition(String className, JSONObject object) {
			name = className;
			if (object.has("size"))
				size = Integer.parseInt(object.getString("size"), 16);
			parent = object.has("parent") ? object.getString("parent") : "";
			fields = new ArrayList<>();
			methods = new ArrayList<>();

			if (parent.equals("System.ValueType")) {
				isValueType = true;
				isEnum = false;
			} else if (parent.equals("System.Enum")) {
				isValueType = false;
				isEnum = true;
			} else {
				isValueType = false;
				isEnum = false;
			}

			if (isEnum) {
				// Get the enums underlying type
				if (object.has("reflection_properties")) {
					underlyingType = object.getJSONObject("reflection_properties").getJSONObject("value__")
							.getString("type");
				} else {
					underlyingType = object.getJSONObject("fields").getJSONObject("value__").getString("type");
				}
			} else {
				underlyingType = "";
			}

			if (object.has("fields")) {
				var classFields = object.getJSONObject("fields");
				for (var fieldName : classFields.keySet()) {
					fields.add(new REField(fieldName, classFields.getJSONObject(fieldName)));
				}
			}

			if (object.has("methods")) {
				var classMethods = object.getJSONObject("methods");
				for (var methodName : classMethods.keySet()) {
					REMethod method = new REMethod(methodName, classMethods.getJSONObject(methodName));
					methods.add(method);
				}
			}
		}

		public boolean hasFields() {
			return !fields.isEmpty();
		}

		public boolean hasParent() {
			return !parent.isEmpty();
		}
	}
}
