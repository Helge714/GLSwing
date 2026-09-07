package com.vingame.glswing.ui;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.vingame.glswing.input.GLInputCodeSupport;

/** Built-in language profiles, file-name detection, and lightweight diagnostics. */
public final class GLCodeLanguages {
	/** Native GLSwing theme language with in-process schema intelligence. */
	public static final GLCodeLanguage GLTHEME = GLThemeCodeSupport.language();
	/** Declarative GLSwing theme-property schema language. */
	public static final GLCodeLanguage GLSCHEMA = GLThemeSchemaCodeSupport.language();
	/** Native GLSwing input-map language with in-process schema intelligence. */
	public static final GLCodeLanguage GLINPUT = GLInputCodeSupport.language();
	/** Native UTF-8 ResourceBundle localization language. */
	public static final GLCodeLanguage LOCALIZATION =
			GLLocalizationCodeSupport.language();
	public static final GLCodeLanguage JAVA = language("java", Rules.JAVA, Set.of(
			"class", "interface", "enum", "record", "public", "private", "protected",
			"static", "final", "void", "new", "return", "if", "else", "for", "while",
			"switch", "case", "break", "continue", "try", "catch", "throw", "extends",
			"implements", "import", "package", "this", "super", "true", "false", "null"));
	public static final GLCodeLanguage C = language("c", Rules.C_LIKE, Set.of(
			"auto", "break", "case", "char", "const", "continue", "default", "do",
			"double", "else", "enum", "extern", "float", "for", "goto", "if", "int",
			"long", "register", "return", "short", "signed", "sizeof", "static",
			"struct", "switch", "typedef", "union", "unsigned", "void", "volatile", "while"));
	public static final GLCodeLanguage CPP = language("cpp", Rules.C_LIKE, Set.of(
			"alignas", "auto", "bool", "break", "case", "catch", "class", "const",
			"constexpr", "continue", "default", "delete", "do", "else", "enum",
			"explicit", "false", "for", "friend", "if", "namespace", "new", "nullptr",
			"operator", "private", "protected", "public", "return", "static", "struct",
			"switch", "template", "this", "throw", "true", "try", "typename", "using",
			"virtual", "void", "while"));
	public static final GLCodeLanguage CSHARP = language("csharp", Rules.C_LIKE, Set.of(
			"abstract", "as", "async", "await", "base", "bool", "break", "case",
			"catch", "class", "const", "continue", "default", "delegate", "do", "else",
			"enum", "false", "finally", "for", "foreach", "if", "interface", "internal",
			"is", "namespace", "new", "null", "override", "private", "protected", "public",
			"readonly", "record", "return", "sealed", "static", "struct", "switch", "this",
			"throw", "true", "try", "using", "var", "virtual", "void", "while", "yield"));
	public static final GLCodeLanguage GLSL = language("glsl", Rules.C_LIKE, Set.of(
			"uniform", "in", "out", "inout", "layout", "const", "struct", "return",
			"if", "else", "for", "while", "discard", "true", "false", "void",
			"float", "int", "uint", "bool", "vec2", "vec3", "vec4", "mat3", "mat4"));
	public static final GLCodeLanguage JAVASCRIPT = language("javascript", Rules.SCRIPT, Set.of(
			"async", "await", "break", "case", "catch", "class", "const", "continue",
			"debugger", "default", "delete", "do", "else", "export", "extends", "false",
			"finally", "for", "function", "if", "import", "in", "instanceof", "let", "new",
			"null", "return", "static", "super", "switch", "this", "throw", "true", "try",
			"typeof", "undefined", "var", "void", "while", "yield"));
	public static final GLCodeLanguage TYPESCRIPT = language("typescript", Rules.SCRIPT, Set.of(
			"any", "as", "async", "await", "boolean", "break", "case", "catch", "class",
			"const", "continue", "declare", "default", "do", "else", "enum", "export",
			"extends", "false", "finally", "for", "from", "function", "if", "implements",
			"import", "interface", "keyof", "let", "namespace", "never", "new", "null",
			"number", "private", "protected", "public", "readonly", "return", "static",
			"string", "super", "switch", "this", "throw", "true", "try", "type", "typeof",
			"undefined", "unknown", "var", "void", "while", "yield"));
	public static final GLCodeLanguage PYTHON = language("python", Rules.PYTHON, Set.of(
			"and", "as", "assert", "async", "await", "break", "class", "continue", "def",
			"del", "elif", "else", "except", "False", "finally", "for", "from", "global",
			"if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass",
			"raise", "return", "True", "try", "while", "with", "yield"));
	public static final GLCodeLanguage RUBY = language("ruby", Rules.RUBY, Set.of(
			"alias", "and", "begin", "break", "case", "class", "def", "defined", "do",
			"else", "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next",
			"nil", "not", "or", "redo", "rescue", "retry", "return", "self", "super", "then",
			"true", "undef", "unless", "until", "when", "while", "yield"));
	public static final GLCodeLanguage JSON = language("json", Rules.JSON, Set.of(
			"true", "false", "null"));
	public static final GLCodeLanguage HTML = language("html", Rules.HTML, Set.of(
			"html", "head", "body", "title", "meta", "link", "script", "style", "main",
			"section", "article", "nav", "header", "footer", "div", "span", "p", "a",
			"img", "button", "input", "label", "form", "table", "ul", "ol", "li"));
	public static final GLCodeLanguage XML = language("xml", Rules.XML, Set.of());
	public static final GLCodeLanguage CSS = language("css", Rules.CSS, Set.of(
			"important", "inherit", "initial", "unset", "none", "auto", "block", "inline",
			"flex", "grid", "absolute", "relative", "fixed", "sticky"));

	private GLCodeLanguages() { }

	/** Resolves a built-in language from a file URI or returns an empty result. */
	public static Optional<GLCodeLanguage> forUri(URI uri) {
		if (uri == null) return Optional.empty();
		String path = uri.getPath();
		if (path == null || path.isBlank()) path = uri.getSchemeSpecificPart();
		return forFileName(path);
	}

	/** Resolves a built-in language from a file name or path. */
	public static Optional<GLCodeLanguage> forFileName(String fileName) {
		if (fileName == null || fileName.isBlank()) return Optional.empty();
		String lower = fileName.toLowerCase(Locale.ROOT);
		int slash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
		if (slash >= 0) lower = lower.substring(slash + 1);
		int dot = lower.lastIndexOf('.');
		String extension = dot < 0 ? "" : lower.substring(dot + 1);
		return Optional.ofNullable(switch (extension) {
		case "gltheme" -> GLTHEME;
		case "glschema" -> GLSCHEMA;
		case "glinput" -> GLINPUT;
		case "properties" -> LOCALIZATION;
		case "java" -> JAVA;
		case "c", "h" -> C;
		case "cc", "cpp", "cxx", "hh", "hpp", "hxx" -> CPP;
		case "cs" -> CSHARP;
		case "glsl", "vert", "frag", "geom", "tesc", "tese", "comp" -> GLSL;
		case "js", "mjs", "cjs", "jsx" -> JAVASCRIPT;
		case "ts", "mts", "cts", "tsx" -> TYPESCRIPT;
		case "py", "pyw" -> PYTHON;
		case "rb" -> RUBY;
		case "json" -> JSON;
		case "html", "htm" -> HTML;
		case "xml" -> XML;
		case "css" -> CSS;
		default -> null;
		});
	}

	private enum Rules { JAVA, C_LIKE, SCRIPT, PYTHON, RUBY, JSON, HTML, XML, CSS }
	private static GLCodeLanguage language(String id, Rules rules,
			Set<String> keywords) {
		return new GLCodeLanguage() {
			@Override public String id() { return id; }
			@Override public String lineCommentPrefix() {
				return switch (rules) {
				case PYTHON, RUBY -> "#";
				case JAVA, C_LIKE, SCRIPT -> "//";
				case JSON, HTML, XML, CSS -> "";
				};
			}
			@Override public boolean supportsOrganizeImports() {
				return rules == Rules.JAVA;
			}
			@Override public boolean supportsDefinitionNavigation() {
				return rules == Rules.JAVA;
			}
			@Override public boolean supportsInlayHints() {
				return rules == Rules.JAVA;
			}
			@Override public List<GLCodeToken> tokenize(String source) {
				List<GLCodeToken> result = new ArrayList<>();
				for (int index = 0; index < source.length();) {
					int start = index;
					char value = source.charAt(index);
					if ((rules == Rules.PYTHON || rules == Rules.RUBY) && value == '#') {
						index = source.indexOf('\n', index);
						if (index < 0) index = source.length();
						result.add(new GLCodeToken(start, index, GLCodeToken.Type.COMMENT));
					} else if ((rules == Rules.JAVA || rules == Rules.C_LIKE
							|| rules == Rules.SCRIPT) && value == '/' && index + 1 < source.length()
							&& source.charAt(index + 1) == '/') {
						index = source.indexOf('\n', index);
						if (index < 0) index = source.length();
						result.add(new GLCodeToken(start, index, GLCodeToken.Type.COMMENT));
					} else if ((rules == Rules.JAVA || rules == Rules.C_LIKE
							|| rules == Rules.SCRIPT || rules == Rules.CSS)
							&& value == '/' && index + 1 < source.length()
							&& source.charAt(index + 1) == '*') {
						int end = source.indexOf("*/", index + 2);
						index = end < 0 ? source.length() : end + 2;
						result.add(new GLCodeToken(start, index, GLCodeToken.Type.COMMENT));
					} else if (rules == Rules.PYTHON && (value == '"' || value == '\'')
							&& index + 2 < source.length() && source.charAt(index + 1) == value
							&& source.charAt(index + 2) == value) {
						String delimiter = "" + value + value + value;
						int end = source.indexOf(delimiter, index + 3);
						index = end < 0 ? source.length() : end + 3;
						result.add(new GLCodeToken(start, index, GLCodeToken.Type.STRING));
					} else if (value == '"' || value == '\'') {
						char quote = value; index++;
						while (index < source.length()) {
							char next = source.charAt(index++);
							if (next == '\\' && index < source.length()) index++;
							else if (next == quote) break;
						}
						result.add(new GLCodeToken(start, index, GLCodeToken.Type.STRING));
					} else if (Character.isJavaIdentifierStart(value)) {
						for (index++; index < source.length()
								&& Character.isJavaIdentifierPart(source.charAt(index)); index++);
						if (keywords.contains(source.substring(start, index)))
							result.add(new GLCodeToken(start, index, GLCodeToken.Type.KEYWORD));
					} else if (Character.isDigit(value)) {
						for (index++; index < source.length()
								&& (Character.isLetterOrDigit(source.charAt(index))
										|| ".xX_".indexOf(source.charAt(index)) >= 0); index++);
						result.add(new GLCodeToken(start, index, GLCodeToken.Type.NUMBER));
					} else index++;
				}
				return List.copyOf(result);
			}
			@Override public List<GLCodeDiagnostic> diagnose(String source) {
				List<GLCodeDiagnostic> result = new ArrayList<>(structuralDiagnostics(source, rules));
				if (rules == Rules.JAVA || rules == Rules.C_LIKE || rules == Rules.SCRIPT)
					result.addAll(redundantSemicolonDiagnostics(source, false));
				else if (rules == Rules.CSS)
					result.addAll(redundantSemicolonDiagnostics(source, true));
				switch (rules) {
					case JAVA -> result.addAll(javaDiagnostics(source));
					case C_LIKE -> result.addAll(cLikeDiagnostics(source));
					case SCRIPT -> result.addAll(scriptDiagnostics(source));
					case PYTHON -> result.addAll(pythonDiagnostics(source));
					case RUBY -> result.addAll(rubyDiagnostics(source));
					case JSON -> result.addAll(jsonDiagnostics(source));
					case HTML -> result.addAll(markupDiagnostics(source, false));
					case XML -> result.addAll(markupDiagnostics(source, true));
					case CSS -> result.addAll(cssDiagnostics(source));
				}
				result.sort(java.util.Comparator.comparingInt(GLCodeDiagnostic::start));
				return List.copyOf(result);
			}
		};
	}
	private static List<GLCodeDiagnostic> redundantSemicolonDiagnostics(String source,
			boolean error) {
		String masked = error ? maskCssCommentsAndLiterals(source)
				: maskCommentsAndLiterals(source);
		List<GLCodeDiagnostic> result = new ArrayList<>();
		int parenthesisDepth = 0;
		for (int index = 0; index < masked.length(); index++) {
			char value = masked.charAt(index);
			if (value == '(') parenthesisDepth++;
			else if (value == ')' && parenthesisDepth > 0) parenthesisDepth--;
			else if (value == ';' && parenthesisDepth == 0) {
				int next = index + 1;
				while (next < masked.length() && (masked.charAt(next) == ' '
						|| masked.charAt(next) == '\t' || masked.charAt(next) == '\r')) next++;
				if (next < masked.length() && masked.charAt(next) == ';')
					result.add(new GLCodeDiagnostic(next, next + 1,
							error ? GLCodeDiagnostic.Severity.ERROR : GLCodeDiagnostic.Severity.WARNING,
							error ? "Unexpected extra semicolon" : "Redundant empty statement"));
			}
		}
		return result;
	}
	private static List<GLCodeDiagnostic> cLikeDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		String masked = maskCommentsAndLiterals(source);
		addMissingSemicolons(source, masked, result);
		addSimpleMethodDiagnostics(source, masked, result);
		addControlContextDiagnostics(masked, result);
		return result;
	}
	private static List<GLCodeDiagnostic> scriptDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		addControlContextDiagnostics(maskCommentsAndLiterals(source), result);
		return result; // JavaScript and TypeScript permit automatic semicolon insertion.
	}
	private static void addControlContextDiagnostics(String masked,
			List<GLCodeDiagnostic> result) {
		boolean hasLoop = java.util.regex.Pattern.compile("\\b(for|while|do)\\b")
				.matcher(masked).find();
		boolean hasSwitch = java.util.regex.Pattern.compile("\\bswitch\\b")
				.matcher(masked).find();
		if (!hasLoop) addKeywordOutsideContext(result, masked, "continue",
				"continue is only valid inside a loop");
		if (!hasLoop && !hasSwitch) addKeywordOutsideContext(result, masked, "break",
				"break is only valid inside a loop or switch");
	}
	private static List<GLCodeDiagnostic> pythonDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		int offset = 0;
		for (String line : source.split("\\n", -1)) {
			String content = line.stripLeading();
			String code = content.split("#", 2)[0].stripTrailing();
			if (line.substring(0, line.length() - content.length()).contains(" \t")
					|| line.substring(0, line.length() - content.length()).contains("\t "))
				result.add(new GLCodeDiagnostic(offset, Math.max(offset + 1,
						offset + line.length() - content.length()),
						GLCodeDiagnostic.Severity.WARNING, "Mixed tabs and spaces in indentation"));
			if (code.matches("(?:async\\s+)?(?:def|class|if|elif|else|for|while|try|except|finally|with|match|case)\\b.*")
					&& !code.endsWith(":")) {
				int start = offset + Math.max(0, line.stripTrailing().length() - 1);
				result.add(new GLCodeDiagnostic(start, start + 1,
						GLCodeDiagnostic.Severity.ERROR, "Python block header requires ':'"));
			}
			offset += line.length() + 1;
		}
		return result;
	}
	private static List<GLCodeDiagnostic> rubyDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		java.util.ArrayDeque<Integer> blocks = new java.util.ArrayDeque<>();
		java.util.regex.Matcher words = java.util.regex.Pattern.compile(
				"(?m)^[ \\t]*(class|module|def|if|unless|case|while|until|for|begin)\\b|\\bdo\\b|^[ \\t]*(end)\\b")
				.matcher(maskCommentsAndLiterals(source));
		while (words.find()) {
			String token = words.group(1) != null ? words.group(1)
					: words.group(2) != null ? words.group(2) : "do";
			if (token.equals("end")) {
				if (blocks.isEmpty()) result.add(new GLCodeDiagnostic(words.start(),
						words.end(), GLCodeDiagnostic.Severity.ERROR, "Unexpected Ruby 'end'"));
				else blocks.pop();
			} else blocks.push(words.start());
		}
		while (!blocks.isEmpty()) {
			int start = blocks.removeLast();
			result.add(new GLCodeDiagnostic(start, start + 1,
					GLCodeDiagnostic.Severity.ERROR, "Ruby block is missing 'end'"));
		}
		return result;
	}
	private static List<GLCodeDiagnostic> jsonDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		java.util.regex.Matcher singleQuote = java.util.regex.Pattern.compile("'[^'\\n]*'")
				.matcher(source);
		while (singleQuote.find()) result.add(new GLCodeDiagnostic(singleQuote.start(),
				singleQuote.end(), GLCodeDiagnostic.Severity.ERROR,
				"JSON strings and property names require double quotes"));
		java.util.regex.Matcher trailingComma = java.util.regex.Pattern.compile(",\\s*[}\\]]")
				.matcher(maskCommentsAndLiterals(source));
		while (trailingComma.find()) result.add(new GLCodeDiagnostic(trailingComma.start(),
				trailingComma.start() + 1, GLCodeDiagnostic.Severity.ERROR,
				"JSON does not allow trailing commas"));
		return result;
	}
	private static List<GLCodeDiagnostic> markupDiagnostics(String source,
			boolean strictXml) {
		record Tag(String name, int offset) { }
		List<GLCodeDiagnostic> result = new ArrayList<>();
		StringBuilder masked = new StringBuilder(source);
		for (int start = source.indexOf("<!--"); start >= 0;) {
			int close = source.indexOf("-->", start + 4);
			if (close < 0) {
				result.add(new GLCodeDiagnostic(start, source.length(),
						GLCodeDiagnostic.Severity.ERROR, "Unclosed markup comment"));
				for (int index = start; index < masked.length(); index++) masked.setCharAt(index, ' ');
				break;
			}
			for (int index = start; index < close + 3; index++) masked.setCharAt(index, ' ');
			start = source.indexOf("<!--", close + 3);
		}
		Set<String> htmlVoid = Set.of("area", "base", "br", "col", "embed", "hr",
				"img", "input", "link", "meta", "param", "source", "track", "wbr");
		java.util.ArrayDeque<Tag> stack = new java.util.ArrayDeque<>();
		java.util.regex.Matcher tags = java.util.regex.Pattern
				.compile("<\\s*(/)?\\s*([A-Za-z][\\w:.-]*)([^<>]*)>")
				.matcher(masked);
		while (tags.find()) {
			boolean closing = tags.group(1) != null;
			String name = strictXml ? tags.group(2)
					: tags.group(2).toLowerCase(java.util.Locale.ROOT);
			boolean selfClosing = tags.group(3).stripTrailing().endsWith("/")
					|| (!strictXml && htmlVoid.contains(name));
			if (closing) {
				if (stack.isEmpty() || !stack.peek().name().equals(name))
					result.add(new GLCodeDiagnostic(tags.start(), tags.end(),
							GLCodeDiagnostic.Severity.ERROR,
							"Unexpected closing tag </" + name + ">"));
				else stack.pop();
			} else if (!selfClosing && !tags.group(2).startsWith("!"))
				stack.push(new Tag(name, tags.start()));
		}
		while (!stack.isEmpty()) {
			Tag tag = stack.removeLast();
			result.add(new GLCodeDiagnostic(tag.offset(),
					Math.min(source.length(), tag.offset() + tag.name().length() + 1),
					GLCodeDiagnostic.Severity.ERROR,
					"Tag <" + tag.name() + "> is not closed"));
		}
		return result;
	}
	private static List<GLCodeDiagnostic> cssDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		String masked = maskCssCommentsAndLiterals(source);
		String[] lines = masked.split("\\n", -1);
		int depth = 0, offset = 0;
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index], trimmed = line.strip();
			int depthAtStart = depth;
			for (int at = 0; at < line.length(); at++) {
				if (line.charAt(at) == '{') depth++;
				else if (line.charAt(at) == '}') depth = Math.max(0, depth - 1);
			}
			if (depthAtStart > 0 && !trimmed.isEmpty() && !trimmed.startsWith("}")
					&& !trimmed.startsWith("@")) {
				boolean property = trimmed.matches("[\\w-]+\\s*:.*");
				boolean propertyNameOnly = trimmed.matches("[\\w-]+\\s+.+");
				if (propertyNameOnly && !property) {
					int start = offset + line.indexOf(trimmed);
					result.add(new GLCodeDiagnostic(start, start + Math.max(1,
							trimmed.indexOf(' ') < 0 ? trimmed.length() : trimmed.indexOf(' ')),
							GLCodeDiagnostic.Severity.ERROR, "CSS declaration requires ':'"));
				} else if (property && !trimmed.endsWith(";") && !trimmed.endsWith("}")) {
					String next = index + 1 < lines.length ? lines[index + 1].strip() : "";
					if (next.matches("[\\w-]+\\s*:.*")) {
						int end = offset + line.stripTrailing().length() - 1;
						result.add(new GLCodeDiagnostic(end, end + 1,
								GLCodeDiagnostic.Severity.ERROR,
								"Missing semicolon between CSS declarations"));
					}
				}
			}
			offset += line.length() + 1;
		}
		return result;
	}
	private static String maskCssCommentsAndLiterals(String source) {
		StringBuilder masked = new StringBuilder(source);
		for (int index = 0; index < source.length();) {
			char value = source.charAt(index);
			if (value == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
				int end = source.indexOf("*/", index + 2); end = end < 0 ? source.length() : end + 2;
				for (int at = index; at < end; at++) if (masked.charAt(at) != '\n') masked.setCharAt(at, ' ');
				index = end;
			} else if (value == '"' || value == '\'') {
				char quote = value; masked.setCharAt(index++, ' ');
				while (index < source.length()) {
					char next = source.charAt(index); if (next != '\n') masked.setCharAt(index, ' '); index++;
					if (next == '\\' && index < source.length()) { if (source.charAt(index) != '\n') masked.setCharAt(index, ' '); index++; }
					else if (next == quote || next == '\n') break;
				}
			} else index++;
		}
		return masked.toString();
	}
	private static List<GLCodeDiagnostic> javaDiagnostics(String source) {
		List<GLCodeDiagnostic> result = new ArrayList<>();
		String masked = maskCommentsAndLiterals(source);
		java.util.regex.Matcher invalidIf = java.util.regex.Pattern
				.compile("\\bif\\s+(?!\\()", java.util.regex.Pattern.MULTILINE)
				.matcher(masked);
		while (invalidIf.find()) result.add(new GLCodeDiagnostic(invalidIf.start(),
				invalidIf.end(), GLCodeDiagnostic.Severity.ERROR,
				"Java if statements require a parenthesized condition"));

		boolean hasLoop = java.util.regex.Pattern.compile("\\b(for|while|do)\\b")
				.matcher(masked).find();
		boolean hasSwitch = java.util.regex.Pattern.compile("\\bswitch\\b")
				.matcher(masked).find();
		if (!hasLoop) addKeywordOutsideContext(result, masked, "continue",
				"continue is only valid inside a loop");
		if (!hasLoop && !hasSwitch) addKeywordOutsideContext(result, masked, "break",
				"break is only valid inside a loop or switch");

		addMissingSemicolons(source, masked, result);
		addSimpleMethodDiagnostics(source, masked, result);
		java.util.regex.Matcher bareExpression = java.util.regex.Pattern
				.compile("(?m)^[ \\t]*([A-Za-z_$][\\w$]*)[ \\t]*;?[ \\t]*$")
				.matcher(masked);
		Set<String> allowedWords = Set.of("else", "try", "finally", "default");
		while (bareExpression.find())
			if (!allowedWords.contains(bareExpression.group(1)))
				result.add(new GLCodeDiagnostic(bareExpression.start(1),
						bareExpression.end(1), GLCodeDiagnostic.Severity.ERROR,
						"A bare identifier is not a valid Java statement or declaration"));
		return result;
	}
	private static void addKeywordOutsideContext(List<GLCodeDiagnostic> result,
			String source, String keyword, String message) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\\b" + keyword + "\\s*;").matcher(source);
		while (matcher.find()) result.add(new GLCodeDiagnostic(matcher.start(),
				matcher.start() + keyword.length(), GLCodeDiagnostic.Severity.ERROR, message));
	}
	private static void addMissingSemicolons(String source, String masked,
			List<GLCodeDiagnostic> result) {
		int offset = 0;
		String[] lines = masked.split("\\n", -1);
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index];
			String trimmed = line.strip();
			boolean definiteKeyword = trimmed.matches(
					"(?:return(?:\\s+.+)?|throw\\s+.+|break|continue|package\\s+.+|import\\s+.+)");
			boolean assignment = trimmed.matches(
					"(?:[\\w.$<>?\\[\\],]+\\s+)+[\\w$]+\\s*(?:=.*)?")
					|| trimmed.matches("[\\w.$\\[\\]]+\\s*(?:=|[+\\-*/%&|^]=).+");
			boolean call = trimmed.matches("[\\w.$]+\\s*\\(.*\\)")
					&& !trimmed.matches("(?:if|for|while|switch|catch|synchronized)\\s*\\(.*");
			String next = "";
			for (int nextIndex = index + 1; nextIndex < lines.length; nextIndex++)
				if (!lines[nextIndex].isBlank()) { next = lines[nextIndex].strip(); break; }
			if ((definiteKeyword || assignment || (call && !next.startsWith("{")))
					&& !trimmed.endsWith(";") && !trimmed.endsWith("{")
					&& !trimmed.endsWith("}")) {
				int end = offset + Math.max(0, line.stripTrailing().length() - 1);
				result.add(new GLCodeDiagnostic(end, Math.min(source.length(), end + 1),
						GLCodeDiagnostic.Severity.ERROR, "Missing semicolon"));
			}
			offset += line.length() + (index + 1 < lines.length ? 1 : 0);
		}
	}
	private static void addSimpleMethodDiagnostics(String source, String masked,
			List<GLCodeDiagnostic> result) {
		java.util.regex.Pattern declaration = java.util.regex.Pattern.compile(
				"(?m)^[ \\t]*(?:(?:public|protected|private|static|final|abstract|synchronized|native|strictfp)\\s+)*+"
				+ "([\\w.$<>?\\[\\]*&]+)\\s+(?:(?:[\\w$]+::)*[~\\w$]+)\\s*\\([^;{}]*\\)\\s*\\{");
		java.util.regex.Matcher methods = declaration.matcher(masked);
		while (methods.find()) {
			int open = masked.indexOf('{', methods.start());
			int close = matchingBrace(masked, open);
			if (close < 0) continue;
			String returnType = methods.group(1);
			String body = masked.substring(open + 1, close);
			java.util.regex.Matcher returns = java.util.regex.Pattern
					.compile("\\breturn\\b([^;]*);").matcher(body);
			boolean valueReturn = false;
			while (returns.find()) {
				boolean hasValue = !returns.group(1).isBlank();
				valueReturn |= hasValue;
				if ((returnType.equals("void") && hasValue)
						|| (!returnType.equals("void") && !hasValue)) {
					int start = open + 1 + returns.start();
					result.add(new GLCodeDiagnostic(start, start + "return".length(),
							GLCodeDiagnostic.Severity.ERROR, returnType.equals("void")
									? "A void method cannot return a value"
									: "A non-void method must return a value"));
				}
			}
			boolean complexFlow = java.util.regex.Pattern
					.compile("\\b(if|switch|for|while|do|try|catch|throw)\\b|->")
					.matcher(body).find();
			if (!returnType.equals("void") && !complexFlow && !valueReturn)
				result.add(new GLCodeDiagnostic(close, close + 1,
						GLCodeDiagnostic.Severity.ERROR,
						"This simple non-void method has no value return"));
		}
	}
	private static int matchingBrace(String source, int open) {
		int depth = 0;
		for (int index = open; index < source.length(); index++) {
			if (source.charAt(index) == '{') depth++;
			else if (source.charAt(index) == '}' && --depth == 0) return index;
		}
		return -1;
	}
	private static String maskCommentsAndLiterals(String source) {
		StringBuilder masked = new StringBuilder(source);
		for (int index = 0; index < source.length();) {
			char value = source.charAt(index);
			if (value == '/' && index + 1 < source.length()
					&& source.charAt(index + 1) == '/') {
				int end = source.indexOf('\n', index + 2);
				if (end < 0) end = source.length();
				for (int at = index; at < end; at++) masked.setCharAt(at, ' ');
				index = end;
			} else if (value == '/' && index + 1 < source.length()
					&& source.charAt(index + 1) == '*') {
				int end = source.indexOf("*/", index + 2);
				end = end < 0 ? source.length() : end + 2;
				for (int at = index; at < end; at++)
					if (masked.charAt(at) != '\n') masked.setCharAt(at, ' ');
				index = end;
			} else if (value == '"' || value == '\'') {
				char quote = value;
				// Preserve one non-whitespace marker so lightweight expression checks
				// still recognize the literal as a value while its contents stay hidden.
				masked.setCharAt(index++, '0');
				while (index < source.length()) {
					char next = source.charAt(index); if (next != '\n') masked.setCharAt(index, ' '); index++;
					if (next == '\\' && index < source.length()) { if (source.charAt(index) != '\n') masked.setCharAt(index, ' '); index++; }
					else if (next == quote || next == '\n') break;
				}
			} else index++;
		}
		return masked.toString();
	}
	private static List<GLCodeDiagnostic> structuralDiagnostics(String source, Rules rules) {
		record Open(char value, int offset) { }
		List<GLCodeDiagnostic> result = new ArrayList<>();
		java.util.ArrayDeque<Open> stack = new java.util.ArrayDeque<>();
		for (int index = 0; index < source.length();) {
			char value = source.charAt(index);
			if ((rules == Rules.JAVA || rules == Rules.C_LIKE || rules == Rules.SCRIPT)
					&& value == '/' && index + 1 < source.length()
					&& source.charAt(index + 1) == '/') {
				int newline = source.indexOf('\n', index + 2);
				index = newline < 0 ? source.length() : newline + 1; continue;
			}
			if ((rules == Rules.JAVA || rules == Rules.C_LIKE || rules == Rules.SCRIPT
					|| rules == Rules.CSS) && value == '/' && index + 1 < source.length()
					&& source.charAt(index + 1) == '*') {
				int end = source.indexOf("*/", index + 2);
				if (end < 0) { result.add(new GLCodeDiagnostic(index,
						Math.max(index + 1, source.length()), GLCodeDiagnostic.Severity.ERROR,
						"Unclosed block comment")); break; }
				index = end + 2; continue;
			}
			if ((rules == Rules.PYTHON || rules == Rules.RUBY) && value == '#') {
				int newline = source.indexOf('\n', index + 1);
				index = newline < 0 ? source.length() : newline + 1; continue;
			}
			if (rules == Rules.PYTHON && (value == '"' || value == '\'')
					&& index + 2 < source.length() && source.charAt(index + 1) == value
					&& source.charAt(index + 2) == value) {
				int start = index; String delimiter = "" + value + value + value;
				int end = source.indexOf(delimiter, index + 3);
				if (end < 0) { result.add(new GLCodeDiagnostic(start, source.length(),
						GLCodeDiagnostic.Severity.ERROR, "Unclosed triple-quoted string")); break; }
				index = end + 3; continue;
			}
			if (value == '"' || value == '\'') {
				int start = index++; boolean closed = false;
				while (index < source.length()) {
					char next = source.charAt(index++);
					if (next == '\\' && index < source.length()) index++;
					else if (next == value) { closed = true; break; }
					else if (next == '\n') break;
				}
				if (!closed) result.add(new GLCodeDiagnostic(start, Math.max(start + 1, index),
						GLCodeDiagnostic.Severity.ERROR, "Unclosed string or character literal"));
				continue;
			}
			if ("([{".indexOf(value) >= 0) stack.push(new Open(value, index));
			else if (")] }".replace(" ", "").indexOf(value) >= 0) {
				char expected = value == ')' ? '(' : value == ']' ? '[' : '{';
				if (stack.isEmpty() || stack.peek().value() != expected)
					result.add(new GLCodeDiagnostic(index, index + 1,
							GLCodeDiagnostic.Severity.ERROR, "Unexpected closing '" + value + "'"));
				else stack.pop();
			}
			index++;
		}
		while (!stack.isEmpty()) {
			Open open = stack.removeLast();
			result.add(new GLCodeDiagnostic(open.offset(), open.offset() + 1,
					GLCodeDiagnostic.Severity.ERROR, "Unclosed '" + open.value() + "'"));
		}
		result.sort(java.util.Comparator.comparingInt(GLCodeDiagnostic::start));
		return List.copyOf(result);
	}
}
