package de.unihd.dbs.uima.annotator.heideltime.substitutions;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubstitutionParser {
	
	static final Pattern paFunction = Pattern.compile("(%[A-Za-z0-9]+?%?|group)\\(");
	static final Pattern paInt = Pattern.compile("[0-9]+");
	Map<String, HashMap<String, String>> allNormalization;
	static final char NULL_DELIM = '\0';
		
	public SubstitutionParser(Map<String, HashMap<String, String>> allNormalization) {
		this.allNormalization = allNormalization;
	}
	
	public Expression parse(String input) {
		Expression res = new LineParser(input).parse();
		assert res.toString().equals(input);
		return res;
	}
	
	public class ParseError extends IllegalArgumentException {
		private static final long serialVersionUID = 1971566690041555187L;
		public ParseError(String msg) {
	        super(msg);
	    }
	}
	
	private class LineParser {
		int start = 0;
		String input;
		Matcher functionMatcher;
		Matcher intMatcher;
		
		public LineParser(String input) {
			this.input = input;
			functionMatcher = paFunction.matcher(input);
			intMatcher = paInt.matcher(input);
		}
		
		public Expression parse() {
			return parseUntil(NULL_DELIM);
		}
		
		private int findEnd(char delim) {
			int res = input.indexOf(delim, start);
			if (res == -1) {
				return input.length();
			}
			return res;
		}

		private Expression parseUntil(char delim) {
			Concatenation res = new Concatenation();
			for (int end = findEnd(delim); start < end; end = findEnd(delim)) {
				boolean foundFn = functionMatcher.find(start);
				if (foundFn) {
					end = Math.min(end, functionMatcher.start());
				}
				if (end > start) {
					res.append(new Literal(input.substring(start, end)));
					start = end;
				} else if (foundFn) {
					start = functionMatcher.end();
					res.append(parseFunction(functionMatcher.group(1)));
				}
			}
			consumeDelimiter(delim);
			return res.simplify();
		}
		
		private void consumeDelimiter(char delim) {
			if (delim != NULL_DELIM) {
				if (input.charAt(start) == delim) {
					start += 1;
				} else {
					throw errorExpecting("'" + delim + "'");
				}
			}
		}

		private Expression parseFunction(String name) {
			if (name.equals("group")) {
				return new MatchGroup(parseInt(')'));
			} else if (name.startsWith("%") && !name.endsWith("%")) {
				Map<String, String> norm = allNormalization.get(name.substring(1));
				if (norm == null) {
					throw errorExpecting("valid normalization function");
				}
				return new Normalization(name.substring(1), norm, parseUntil(')'));
			} else if ("%SUBSTRING%".equals(name)) {
				return new Substring(parseUntil(','), parseInt(','), parseInt(')'));
			} else if ("%UPPERCASE%".equals(name)) {
				return new Uppercase(parseUntil(')'));
			} else if ("%LOWERCASE%".equals(name)) {
				return new Lowercase(parseUntil(')'));
			} else if ("%SUM%".equals(name)) {
				return new Sum(parseUntil(','), parseUntil(')'));
			}
			throw errorExpecting("function call beginning '%'");
		}
		
		private int parseInt(char delim) {
			if (!intMatcher.find(start) || intMatcher.start() != start) {
				throw errorExpecting("integer");
			}
			start = intMatcher.end();
			consumeDelimiter(delim);
			return Integer.parseInt(intMatcher.group());
		}
		
		private RuntimeException errorExpecting(String expected) {
			return new ParseError("Expected " + expected + " at '^': " +
					input.substring(0, start) + "^" + input.substring(start));
		}
	}
	
	
}
