package page.editor

import org.treesitter.TreeSitterBash
import org.treesitter.TreeSitterC
import org.treesitter.TreeSitterClojure
import org.treesitter.TreeSitterCpp
import org.treesitter.TreeSitterCss
import org.treesitter.TreeSitterDart
import org.treesitter.TreeSitterDockerfile
import org.treesitter.TreeSitterElixir
import org.treesitter.TreeSitterGo
import org.treesitter.TreeSitterHaskell
import org.treesitter.TreeSitterHtml
import org.treesitter.TreeSitterJavascript
import org.treesitter.TreeSitterJson
import org.treesitter.TreeSitterLua
import org.treesitter.TreeSitterMarkdown
import org.treesitter.TreeSitterOcaml
import org.treesitter.TreeSitterPerl
import org.treesitter.TreeSitterPhp
import org.treesitter.TreeSitterPython
import org.treesitter.TreeSitterR
import org.treesitter.TreeSitterRuby
import org.treesitter.TreeSitterRust
import org.treesitter.TreeSitterScala
import org.treesitter.TreeSitterSql
import org.treesitter.TreeSitterSvelte
import org.treesitter.TreeSitterSwift
import org.treesitter.TreeSitterTypescript
import org.treesitter.TreeSitterVue
import org.treesitter.TreeSitterYaml
import org.treesitter.TreeSitterZig

object TreeSitterLexers {

    val python: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterPython,
            overrides = mapOf(
                "string_content" to TokenKind.STRING,
                "concatenated_string" to TokenKind.STRING,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "none" to TokenKind.KEYWORD,
            ),
        )
    }

    val javascript: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterJavascript,
            overrides = mapOf(
                "template_string" to TokenKind.STRING,
                "regex" to TokenKind.STRING,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "undefined" to TokenKind.KEYWORD,
            ),
        )
    }

    val typescript: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterTypescript,
            overrides = mapOf(
                "template_string" to TokenKind.STRING,
                "regex" to TokenKind.STRING,
                "type_annotation" to TokenKind.TYPE,
                "predefined_type" to TokenKind.TYPE,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "undefined" to TokenKind.KEYWORD,
            ),
        )
    }

    val go: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterGo,
            overrides = mapOf(
                "interpreted_string_literal" to TokenKind.STRING,
                "raw_string_literal" to TokenKind.STRING,
                "rune_literal" to TokenKind.STRING,
                "int_literal" to TokenKind.NUMBER,
                "float_literal" to TokenKind.NUMBER,
                "imaginary_literal" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "nil" to TokenKind.KEYWORD,
            ),
        )
    }

    val rust: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterRust,
            overrides = mapOf(
                "raw_string_literal" to TokenKind.STRING,
                "char_literal" to TokenKind.STRING,
                "boolean_literal" to TokenKind.KEYWORD,
                "attribute_item" to TokenKind.ANNOTATION,
                "inner_attribute_item" to TokenKind.ANNOTATION,
                "primitive_type" to TokenKind.TYPE,
            ),
        )
    }

    val c: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterC,
            overrides = mapOf(
                "system_lib_string" to TokenKind.STRING,
                "char_literal" to TokenKind.STRING,
                "number_literal" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "primitive_type" to TokenKind.TYPE,
                "sized_type_specifier" to TokenKind.TYPE,
            ),
        )
    }

    val cpp: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterCpp,
            overrides = mapOf(
                "system_lib_string" to TokenKind.STRING,
                "raw_string_literal" to TokenKind.STRING,
                "char_literal" to TokenKind.STRING,
                "number_literal" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "nullptr" to TokenKind.KEYWORD,
                "primitive_type" to TokenKind.TYPE,
                "sized_type_specifier" to TokenKind.TYPE,
                "namespace_identifier" to TokenKind.TYPE,
            ),
        )
    }

    val ruby: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterRuby,
            overrides = mapOf(
                "string_content" to TokenKind.STRING,
                "heredoc_body" to TokenKind.STRING,
                "symbol" to TokenKind.STRING,
                "regex" to TokenKind.STRING,
                "integer" to TokenKind.NUMBER,
                "float" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "nil" to TokenKind.KEYWORD,
            ),
        )
    }

    val bash: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterBash,
            overrides = mapOf(
                "raw_string" to TokenKind.STRING,
                "string_content" to TokenKind.STRING,
                "word" to TokenKind.IDENTIFIER,
                "variable_name" to TokenKind.IDENTIFIER,
            ),
        )
    }

    val html: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterHtml,
            overrides = mapOf(
                "attribute_value" to TokenKind.STRING,
                "tag_name" to TokenKind.KEYWORD,
                "attribute_name" to TokenKind.IDENTIFIER,
            ),
        )
    }

    val css: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterCss,
            overrides = mapOf(
                "string_value" to TokenKind.STRING,
                "color_value" to TokenKind.NUMBER,
                "integer_value" to TokenKind.NUMBER,
                "float_value" to TokenKind.NUMBER,
                "plain_value" to TokenKind.IDENTIFIER,
                "property_name" to TokenKind.IDENTIFIER,
                "class_name" to TokenKind.TYPE,
                "id_name" to TokenKind.TYPE,
                "tag_name" to TokenKind.KEYWORD,
            ),
        )
    }

    val json: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterJson,
            overrides = mapOf(
                "string_content" to TokenKind.STRING,
                "number" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
            ),
        )
    }

    val yaml: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterYaml,
            overrides = mapOf(
                "string_scalar" to TokenKind.STRING,
                "double_quote_scalar" to TokenKind.STRING,
                "single_quote_scalar" to TokenKind.STRING,
                "block_scalar" to TokenKind.STRING,
                "boolean_scalar" to TokenKind.KEYWORD,
                "null_scalar" to TokenKind.KEYWORD,
                "integer_scalar" to TokenKind.NUMBER,
                "float_scalar" to TokenKind.NUMBER,
                "anchor_name" to TokenKind.ANNOTATION,
                "alias_name" to TokenKind.ANNOTATION,
                "tag" to TokenKind.TYPE,
            ),
        )
    }

    val lua: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterLua,
            overrides = mapOf(
                "string_content" to TokenKind.STRING,
                "number" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "nil" to TokenKind.KEYWORD,
            ),
        )
    }

    val swift: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterSwift,
            overrides = mapOf(
                "line_string_literal" to TokenKind.STRING,
                "multi_line_string_literal" to TokenKind.STRING,
                "regex_literal" to TokenKind.STRING,
                "integer_literal" to TokenKind.NUMBER,
                "real_literal" to TokenKind.NUMBER,
                "boolean_literal" to TokenKind.KEYWORD,
                "nil" to TokenKind.KEYWORD,
                "attribute" to TokenKind.ANNOTATION,
            ),
        )
    }

    val scala: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterScala,
            overrides = mapOf(
                "string" to TokenKind.STRING,
                "interpolated_string" to TokenKind.STRING,
                "character_literal" to TokenKind.STRING,
                "integer_literal" to TokenKind.NUMBER,
                "floating_point_literal" to TokenKind.NUMBER,
                "boolean_literal" to TokenKind.KEYWORD,
                "null_literal" to TokenKind.KEYWORD,
                "annotation" to TokenKind.ANNOTATION,
            ),
        )
    }

    val haskell: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterHaskell,
            overrides = mapOf(
                "string" to TokenKind.STRING,
                "char" to TokenKind.STRING,
                "integer" to TokenKind.NUMBER,
                "float" to TokenKind.NUMBER,
                "constructor" to TokenKind.TYPE,
                "type_name" to TokenKind.TYPE,
            ),
        )
    }

    val ocaml: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterOcaml,
            overrides = mapOf(
                "string" to TokenKind.STRING,
                "character" to TokenKind.STRING,
                "number" to TokenKind.NUMBER,
                "boolean" to TokenKind.KEYWORD,
                "constructor_name" to TokenKind.TYPE,
                "type_constructor" to TokenKind.TYPE,
            ),
        )
    }

    val elixir: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterElixir,
            overrides = mapOf(
                "string" to TokenKind.STRING,
                "charlist" to TokenKind.STRING,
                "atom" to TokenKind.STRING,
                "sigil" to TokenKind.STRING,
                "integer" to TokenKind.NUMBER,
                "float" to TokenKind.NUMBER,
                "boolean" to TokenKind.KEYWORD,
                "nil" to TokenKind.KEYWORD,
            ),
        )
    }

    val clojure: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterClojure,
            overrides = mapOf(
                "str_lit" to TokenKind.STRING,
                "regex_lit" to TokenKind.STRING,
                "num_lit" to TokenKind.NUMBER,
                "kwd_lit" to TokenKind.KEYWORD,
                "bool_lit" to TokenKind.KEYWORD,
                "nil_lit" to TokenKind.KEYWORD,
                "sym_lit" to TokenKind.IDENTIFIER,
            ),
        )
    }

    val perl: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterPerl,
            overrides = mapOf(
                "string_single_quoted" to TokenKind.STRING,
                "string_double_quoted" to TokenKind.STRING,
                "heredoc_content" to TokenKind.STRING,
                "regex_pattern" to TokenKind.STRING,
                "number" to TokenKind.NUMBER,
                "integer" to TokenKind.NUMBER,
            ),
        )
    }

    val r: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterR,
            overrides = mapOf(
                "string" to TokenKind.STRING,
                "float" to TokenKind.NUMBER,
                "integer" to TokenKind.NUMBER,
                "complex" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "na" to TokenKind.KEYWORD,
                "inf" to TokenKind.KEYWORD,
            ),
        )
    }

    val php: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterPhp,
            overrides = mapOf(
                "string_value" to TokenKind.STRING,
                "encapsed_string" to TokenKind.STRING,
                "heredoc_body" to TokenKind.STRING,
                "nowdoc_body" to TokenKind.STRING,
                "integer" to TokenKind.NUMBER,
                "float" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "named_type" to TokenKind.TYPE,
                "attribute" to TokenKind.ANNOTATION,
            ),
        )
    }

    val sql: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterSql,
            overrides = mapOf(
                "string" to TokenKind.STRING,
                "number" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
            ),
        )
    }

    val markdown: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterMarkdown,
            overrides = mapOf(
                "code_span" to TokenKind.STRING,
                "code_fence_content" to TokenKind.STRING,
                "link_destination" to TokenKind.STRING,
                "uri_autolink" to TokenKind.STRING,
                "heading_content" to TokenKind.KEYWORD,
                "link_text" to TokenKind.IDENTIFIER,
            ),
        )
    }

    val dart: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterDart,
            overrides = mapOf(
                "string_literal" to TokenKind.STRING,
                "template_substitution" to TokenKind.IDENTIFIER,
                "decimal_integer_literal" to TokenKind.NUMBER,
                "hex_integer_literal" to TokenKind.NUMBER,
                "decimal_floating_point_literal" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null_literal" to TokenKind.KEYWORD,
                "marker_annotation" to TokenKind.ANNOTATION,
                "type_identifier" to TokenKind.TYPE,
            ),
        )
    }

    val zig: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterZig,
            overrides = mapOf(
                "string_literal" to TokenKind.STRING,
                "char_literal" to TokenKind.STRING,
                "integer_literal" to TokenKind.NUMBER,
                "float_literal" to TokenKind.NUMBER,
                "true" to TokenKind.KEYWORD,
                "false" to TokenKind.KEYWORD,
                "null" to TokenKind.KEYWORD,
                "undefined" to TokenKind.KEYWORD,
                "builtin" to TokenKind.ANNOTATION,
            ),
        )
    }

    val dockerfile: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterDockerfile,
            overrides = mapOf(
                "double_quoted_string" to TokenKind.STRING,
                "single_quoted_string" to TokenKind.STRING,
                "unquoted_string" to TokenKind.IDENTIFIER,
                "image_name" to TokenKind.IDENTIFIER,
                "image_tag" to TokenKind.STRING,
            ),
        )
    }

    val vue: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterVue,
            overrides = mapOf(
                "attribute_value" to TokenKind.STRING,
                "tag_name" to TokenKind.KEYWORD,
                "attribute_name" to TokenKind.IDENTIFIER,
                "directive_name" to TokenKind.ANNOTATION,
            ),
        )
    }

    val svelte: SyntaxLexer by lazy {
        TreeSitterGenericLexer(
            languageFactory = ::TreeSitterSvelte,
            overrides = mapOf(
                "attribute_value" to TokenKind.STRING,
                "tag_name" to TokenKind.KEYWORD,
                "attribute_name" to TokenKind.IDENTIFIER,
            ),
        )
    }
}
