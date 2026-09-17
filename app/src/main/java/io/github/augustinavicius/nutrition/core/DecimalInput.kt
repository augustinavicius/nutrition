package io.github.augustinavicius.nutrition.core

/**
 * Number entry that does not care which decimal separator reaches it.
 *
 * The same keyboard key produces a comma on a Lithuanian layout and a full stop on a British
 * one, and packaging in either country states the same quantity as "2,6" or "2.6". Both are
 * accepted wherever a figure is typed, and both mean the same number, so a field never has to
 * be retyped because the keyboard offered the other one.
 */
object DecimalInput {

    /**
     * What a number field is allowed to hold: digits and at most one separator.
     *
     * The character the user typed is kept rather than rewritten, so nothing shifts under the
     * cursor mid-edit; [parse] is what makes the two equivalent. Anything else a keyboard can
     * produce — a second separator, a stray sign, whitespace — is dropped as it arrives.
     */
    fun sanitize(text: String): String = buildString {
        var separatorTaken = false
        for (char in text) {
            when {
                char.isDigit() -> append(char)
                (char == '.' || char == ',') && !separatorTaken -> {
                    separatorTaken = true
                    append(char)
                }
            }
        }
    }

    /** The number [text] states, or null when it states none. */
    fun parse(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

    /** As [parse], but blank means "left empty" rather than "not a number". */
    fun parseOptional(text: String): Double? = text.takeIf { it.isNotBlank() }?.let(::parse)
}
