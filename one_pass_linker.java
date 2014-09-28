/*
* This is a one-pass implementation of a linker.
*
* The target machine is word addressable and has a memory of 600 words, each consisting of 4 decimal deigits
* The first digit is the opcode, which is unchanged by the linnker. The remaining three digits are either:
* - (I) an immediate operand
* - (A) an absolute address, which is unchanged
* - (R) a relative address
* - (E) an external address, which is resolved
* 
* The first module has base address zero; the base address for module I+1 is equal to the
* base address of module I plus the length of module I. The absolute address for symbol S defined in module
* M is the base address of M plus the relative address of S within M.
*
* This implementation processes the input only once. For each module, the base address and absolute address for each external symbol is determined. 
*
* If a program instruction uses an external address that has not been defined, we keep track of said symbol and the unresolved address.
* Once the symbol has been defined, we go back to the unresolved address and dynamically resolve it.
* 
* The use list is a count NU followed by the NU external symbols used in the module. An E reference in the
* program text with address K represents the Kth symbol in the use list, using 0-based counting. For example,
* if the use list is ‘‘2 f g’’, then an instruction ‘‘E 7000’’ refers to f, and an instruction ‘‘E 5001’’ refers to g.
*/


import java.util.Scanner;
import java.io.*;
import java.util.*;

public class one_pass_linker {

	// Initialize global variables
	static Map<String, Integer> symbolsMap = new HashMap<String, Integer>(); // Store symbols and declared variables
	static ArrayList resolved_list = new ArrayList(); // Store list to be printed out

	public static void main(String[] args) {

		// Use scanner to prompt input file 
		Scanner input_file = scanner(args[0]);
		first_pass(input_file);
	}

	// TODO: Add Javadoc
	public static Scanner scanner(String input_file) {

		try {
			Scanner buffered_file = new Scanner(new BufferedReader(new FileReader(input_file)));
			return buffered_file;
		} 

		catch(Exception e) {
			System.out.println("Error in scanning file " + input_file);
		}

		return null; // Not supposed to go here
	}

	public static void first_pass(Scanner input_file) {

		// Declare global variables
		int line_current = 0; // Counts current line number
		int line_length = 0; // Counts length of current line
		int current_module = 0; // Counts current module
		int module_current = 0; // Tracks current module
		int module_offset = 0; // Stores total module offsets
		int current_offset = 0; // Tracks the offset of each address

		String symbol = "";
		int address = 0;
		String use = "";

		ArrayList<String> current_symbolList = new ArrayList<String>(); // List of all symbols defined in current module
		ArrayList all_symbolList = new ArrayList(); // List of all symbols
		ArrayList current_useList = new ArrayList(); // List of all used symbols in current module
		ArrayList all_useList = new ArrayList(); // List of all used symbols
		ArrayList<String> undefined_current_useList = new ArrayList<String>(); // List of all used symbols that are undefined during the pass

		ArrayList<ArrayList> unresolved_instruction = new ArrayList<ArrayList>(); // Map of all unresolved symbols and their index at resolved_list


		while (input_file.hasNext()) {

			// First, store the length of line (first integer in the line)
			line_length = input_file.nextInt();

			// Update current line
			line_current++;

			// If line is the first line of a module, it is definition list
			if ((line_current-1) % 3 == 0) {
				for (int i = 0; i < line_length; i++) {
					symbol = input_file.next();
					address = input_file.nextInt() + module_offset; // Stores absolute address directly to symbol
					// Error Check: checks whether symbol is already defined
					if (symbolsMap.containsKey(symbol)) {
						System.out.println("\nError: " + symbol + " is multiply defined" + ". First value " + symbolsMap.get(symbol) + " is used");
					} else { 

						symbolsMap.put(symbol, address); // Passes check, put into symbols map
						all_symbolList.add(symbol); 
						current_symbolList.add(symbol);

						// Check undefined_current_useList array. 
						// Dynamically resolve undefined symbols in resolved_list
						if (undefined_current_useList.contains(symbol)) {

							// Find index at resolved_list where undefined symbols exist
							for (int x = 0; x < unresolved_instruction.size(); x++) {

								if (unresolved_instruction.get(x).get(0).equals(symbol)) {
									Integer index = (Integer) unresolved_instruction.get(x).get(1);
									//unresolved_instruction.remove(x);
									//System.out.println(index);
									ArrayList update_instruction = new ArrayList();
									update_instruction = (ArrayList) resolved_list.get(index);
									// System.out.println("Instruction need to be updated is" + update_instruction);

									Integer external = (Integer) update_instruction.get(1);
									external = external - (external % 1000) + address;

									update_instruction.set(1, external);

									resolved_list.set(index, update_instruction);
									// System.out.println(unresolved_instruction);
								}

							}

							// Remove resolved symbols from unresolved_instruction list
							for (int x = 0; x < unresolved_instruction.size(); x++) {
								if (unresolved_instruction.get(x).get(0).equals(symbol)) {
									unresolved_instruction.remove(x);
								}
							}

							undefined_current_useList.remove(symbol);
						}

					}
				}
				module_current++;
			}

			// If line is the second line of a module, it is use list
			else if ((line_current-2) % 3 == 0) {
				for (int i = 0; i < line_length; i++) {
					use = input_file.next();
					current_useList.add(use);
					if (!all_useList.contains(use)) {
						all_useList.add(use);
					}

					// If current symbol has not been defined, add to undefined_current_useList 
					if (!symbolsMap.containsKey(use)) {
						if (!undefined_current_useList.contains(use)) {
							undefined_current_useList.add(use); // Check for repetitions in undefined_current_useList
						}
					}
				}
			}

			// If line is the third line of a module, it is the program text
			else if ((line_current-3) % 3 == 0) {

				current_offset++;
				ArrayList current_accessList = new ArrayList(); // List of all access digits used to access current_useList in this module - for error checking

				// Error check: check if address appearing in definition list of current module exceeds size of the module
				// Treat address as 0 (relative) if so
				for (String check_symbol : current_symbolList) {
					if (symbolsMap.get(check_symbol) - module_offset > line_length) {
						symbolsMap.put(check_symbol, 0); // Set address value as 0 
						System.out.printf("Error: address of symbol %s exceeds size of current module", check_symbol);
					}
				}

				// Sort all instructions
				for (int i = 0; i < line_length; i ++) {

					String instruction = input_file.next();
					Integer instruction_address = input_file.nextInt();
					
					ArrayList current_instruction = new ArrayList();

					// If absolute or immediate address, then put into resolved_list the address as is
					if (instruction.equals("A") || instruction.equals("I")) {

						current_instruction.add(instruction); // Put current instruction into ArrayList<Instruction, Instruction Address>

						// Error Check: checks if absolute address excees size of the machine (600)
						if (instruction.equals("A") && (instruction_address % 1000) > 599 ) {
							instruction_address = instruction_address - (instruction_address % 1000); // Use value zero
							current_instruction.add(instruction_address);
							String error_string = "Error: Absolute address exceeds machine size. Value zero is used";
							current_instruction.add(error_string);
						} else {
							current_instruction.add(instruction_address); // Passes error check
						}
					}

					// If relative address, then update the address according to the current module offset
					else if (instruction.equals("R")) {

						// Error Check: checks if relative address exceeds size of the module
						if ((instruction_address % 1000) > line_length) {
							instruction_address = instruction_address - (instruction_address % 1000); // Use value zero
							String error_string = "Error: Relative address exceeds size of the module. Value zero is used";
							current_instruction.add(instruction);
							current_instruction.add(instruction_address);
							current_instruction.add(error_string);
						} else {
							Integer resolved_instruction_address = (Integer) instruction_address + (Integer) module_offset;
							current_instruction.add(instruction); // Put current instruction into ArrayList<Instruction, Instruction Address>
							current_instruction.add(resolved_instruction_address); 
						}	
					}

					// If external address, access current_useList and check if defined
					// If symbol is already defined, immediately resolve address
					// If symbol is not defined, track in unresolved_instruction
					else {

						// Find the external symbol
						int access_digit = instruction_address % 1000; // Index of current_useList

						// Error check: checks if external address is too large to reference an entry
						if (access_digit > current_useList.size()-1) {
							// Treat address as immediate
							current_instruction.add(instruction);
							current_instruction.add(instruction_address);
							String error_string = "Error: External address exceeds length of use list. Treated as immediate address";
							current_instruction.add(error_string);

						} else {

							current_accessList.add(access_digit); // Add access digit to current_accessList for error checking

							String access_symbol = (String) current_useList.get(access_digit);

							// Check whether symbol has been defined or not
							if (!undefined_current_useList.contains(access_symbol)) {
								// Symbol has been defined
								Integer external = (Integer) symbolsMap.get(access_symbol);
								instruction_address = instruction_address - access_digit; // Subtract address by its last digit
								instruction_address = instruction_address + external; // Resolve external address
							}

							// If symbol has not been defined, track in unresolved_instruction
							else {
								ArrayList add_instruction = new ArrayList();
								add_instruction.add(access_symbol);
								add_instruction.add(i+module_offset);
								unresolved_instruction.add(add_instruction);
							}

							//System.out.printf("This is E read number %d \n", i);
							current_instruction.add(instruction); // Put current instruction into ArrayList<Instruction, Instruction Address>
							current_instruction.add(instruction_address); 
						}
					}

					resolved_list.add(current_instruction);
				}

				// Error Check: check if symbol appears in a use list but not actually used in module
				for (int z = 0; z < current_useList.size(); z++) {
					if (!current_accessList.contains(z)) {
						System.out.printf("\nWarning: Symbol %s appears in a use list but not actually used in module\n", current_useList.get(z));
					}
				}

				// Update module offset
				module_offset = module_offset + line_length; 

				current_useList.clear(); // Clear current_useList after each module
				current_symbolList.clear(); // Clear current_symbolList after each module
				current_accessList.clear();
			}
		}

		// Error Check: check for symbols that were used but never defined
		// Then resolve the symbol using the value zero
		if (!undefined_current_useList.isEmpty()) {

			for (int y = 0; y < undefined_current_useList.size(); y++) {
				String error_symbol = undefined_current_useList.get(y);
				// Find index at resolved_list where undefined symbols exist
				for (int x = 0; x < unresolved_instruction.size(); x++) {
					if (unresolved_instruction.get(x).get(0).equals(error_symbol)) {
						Integer index = (Integer) unresolved_instruction.get(x).get(1);
						ArrayList update_instruction = new ArrayList();
						update_instruction = (ArrayList) resolved_list.get(index);

						Integer external = (Integer) update_instruction.get(1);
						external = external - (external % 1000) + 0; // Resolve using value zero

						update_instruction.set(1, external);
						String error_string = "Error: " + error_symbol + " is not defined. Zero value used";
						update_instruction.add(error_string);

						resolved_list.set(index, update_instruction);
						// System.out.println(unresolved_instruction);
					}

				}

				// Remove resolved symbols from unresolved_instruction list
				for (int x = 0; x < unresolved_instruction.size(); x++) {
					if (unresolved_instruction.get(x).get(0).equals(error_symbol)) {
						unresolved_instruction.remove(x);
					}
				}

				undefined_current_useList.remove(error_symbol);
			}
		}	
		

		// Print Symbols Table
		System.out.println("\nSymbol Table");
		for (String print_symbol : symbolsMap.keySet()) {
			Integer print_symbol_address = symbolsMap.get(print_symbol);
			System.out.printf("%s=%d\n", print_symbol, print_symbol_address);
		}

		// Print Memory Map
		System.out.println("\nMemory Map");
		for (int m = 0; m < resolved_list.size(); m++) {
			ArrayList print_memory_address = new ArrayList();
			print_memory_address = (ArrayList) resolved_list.get(m);

			// If there is an error message, print.
			if (print_memory_address.size() == 3) {
				System.out.printf("%d:  %d %s\n", m, print_memory_address.get(1), print_memory_address.get(2));
			} else {
			System.out.printf("%d:  %d\n", m, print_memory_address.get(1));
			}	
		}

		// Error Check: symbols that were defined but never used
		System.out.println("");
		for (String check_symbol : symbolsMap.keySet()) {
			if (!all_useList.contains(check_symbol)) {
				System.out.printf("Warning: Symbol %s is defined but not used\n", check_symbol);
			}
		}
	}
}