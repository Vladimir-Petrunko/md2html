/////////////////////////////////////////////////////////////////////////////////////////
//////-----------------------------------------------------------------------------\/////
/////   **     **   **          *****        *    *   *******   **     **   *       \////
///|    * *   * *   *  *             *       *    *      *      * *   * *   *        |///
///|    *   *   *   *   *       *****        ******      *      *   *   *   *        |///
///|    *       *   *  *       *             *    *      *      *       *   *        |///
////\   *       *   **          *****        *    *      *      *       *   *****   /////
/////\_____________________________________________________________________________//////
/////////////////////////////////////////////////////////////////////////////////////////

import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

class Converter {

    /**
     * Maps supported tags in Markdown to their equivalent in HTML.
     */
    private final HashMap<String, String> tags = new HashMap<>();

    /**
     * Maximum length of a valid tag
     */
    private final int MAX_TAG_LENGTH = 2;

    /**
     * Maps special symbols to their codes in HTML.
     */
    private final HashMap<Character, String> codes = new HashMap<>();

    /**
     * Holds converted paragraphs.
     */
    private StringBuilder global = new StringBuilder();

    /**
     * Hold a list of currently active (i.e. that can be closed) tags
     */
    private HashMap<String, Integer> open = new HashMap<>();

    /**
     * Holds a partition of the currently read text in a such a way,
     * that their concatenation equals the currently read text (with
     * some text possibly interpreted as HTML markup)
     */
    private LinkedList<StringBuilder> contents = new LinkedList<>();

    /**
     * Holds initial paragraphs.
     */
    private String paragraph;

    private int lastParenthesis;

    /**
     * Returns the matching parenthesis to <code>c</code>.
     *
     * @param c     the parenthesis (round or square)
     * @return      the matching parenthesis
     */
    private char matchingParenthesis(char c) {
        if (c == '(') {
            return ')';
        } else if (c == ')') {
            return '(';
        } else if (c == ']') {
            return '[';
        } else {
            return ']';
        }
    }

    public Converter() {
        // Markdown -> HTML
        tags.put("*", "em");
        tags.put("_", "em");
        tags.put("**", "strong");
        tags.put("__", "strong");
        tags.put("`", "code");
        tags.put("--", "s");
        tags.put("~", "mark");

        // HTML reserved characters
        codes.put('<', "&lt;");
        codes.put('>', "&gt;");
        codes.put('&', "&amp;");
        codes.put('\'', "&apos;");
        codes.put('"', "&quot;");
    }

    public String parseParagraph(String paragraph) {
        this.global = new StringBuilder();
        this.open = new HashMap<>();
        this.contents = new LinkedList<>();
        this.paragraph = paragraph;
        for (int i = 0; i < paragraph.length(); i++) {
            if (paragraph.charAt(i) == ')') {
                lastParenthesis = i;
            }
        }
        parseParagraph();
        return global.toString();
    }

    /**
     * Appends an HTML tag to <code>global</code>.
     *
     * @param tag   the tag (in HTML) to be appended
     * @param attr  the attributes of the tag (in HTML, full-form)
     * @param open  whether the tag is an opening tag
     */
    private void appendLiteralTag(StringBuilder sb, String tag, String attr, boolean open) {
        if (open) {
            sb.append("<").append(tag).append(attr).append(">");
        } else {
            sb.append("</").append(tag).append(">");
        }
    }

    /**
     * Converts a tag in Markdown to HTML and appends it to <code>global</code>.
     *
     * @param tag   the tag (in Markdown) to be appended
     * @param attr  the attributes of the tag (in HTML, full-form)
     * @param open  whether the tag is an opening tag
     */
    private void appendTag(StringBuilder sb, String tag, String attr, boolean open) {
        appendLiteralTag(sb, tags.get(tag), attr, open);
    }

    /**
     * Returns max-length valid HTML tag with start at given index.
     *
     * @param index   the index of the start of the potential tag
     * @return        the max-length valid HTML tag starting at index, or <code>null</code> if none exists
     */
    private String getTag(int index) {
        int last = Math.min(paragraph.length(), index + MAX_TAG_LENGTH);
        String sub = paragraph.substring(index, last);
        while (!sub.isEmpty()) {
            if (tags.containsKey(sub)) {
                return sub;
            }
            sub = sub.substring(0, sub.length() - 1);
        }
        return null;
    }

    /**
     * Iterates through the last elements in the partition of the currently
     * read text until it meets an element exactly equal to <code>start</code>.
     * Everything after this element is joined together and surrounded with the
     * tags (or literal text) corresponding to <code>start</code> and <code>end</code>.
     *
     * @param start     the start tag of the block to be collected
     * @param end       the end tag of the block to be collected
     * @param makeTags  <code>true</code> if tags should be interpreted as Markdown,
     *                  <code>false</code> if as literal (non-markup) text
     */
    private void collect(String start, String end, boolean makeTags) {
        StringBuilder inside = new StringBuilder();
        while (true) {
            StringBuilder last = contents.peekLast();
            if (last.length() == start.length() && last.toString().equals(start)) { // Met opening tag!
                StringBuilder sb = new StringBuilder();
                // Form pair
                if (makeTags) {
                    appendTag(sb, start, "", true);
                    sb.append(inside);
                    appendTag(sb, end, "", false);
                } else {
                    sb.append(start).append(inside).append(end);
                }
                // Replace initial pieces with what was collected, plus the outside tags as HTML
                contents.pollLast();
                contents.addLast(sb);
                open.remove(start);
                break;
            } else {
                if (tags.containsKey(last.toString())) {
                    // Current tag is being appended as plain text,
                    // so it is no longer a valid opening tag :(
                    open.remove(last.toString());
                }
                inside = last.append(inside);
                contents.pollLast();
            }
        }
    }

    /**
     * Appends the remaining items in <code>contents</code>
     * to <code>global</code>, from first to last.
     */
    private void clean() {
        while (!contents.isEmpty()) {
            global.append(contents.pollFirst());
        }
    }

    /**
     * Returns the nearest character that can be read from position <code>k</code>.
     * If the character is escaped, skips the escape symbol (\) and returns the
     * character that follows.
     *
     * @param k     the current position
     * @return      a StringBuilder containing the next char, or an empty StringBuilder if none is available
     */
    private StringBuilder readChar(int k) {
        char c;
        if (paragraph.charAt(k) == '\\') {
            if (k + 1 >= paragraph.length()) {
                return new StringBuilder();
            } else {
                c = paragraph.charAt(k + 1);
            }
        } else {
            c = paragraph.charAt(k);
        }
        return new StringBuilder(codes.containsKey(c) ? codes.get(c) : (c + ""));
    }

    /**
     * Appends an HTML link with the given attribute and text
     * to <code>contents</code>
     *
     * @param attr      attribute (i.e. target) of link
     * @param text    text of link
     */
    private void appendLink(String attr, String text) {
        StringBuilder link = new StringBuilder();
        link.append("<a href='").append(attr).append("'>").append(text).append("</a>");
        contents.addLast(link);
    }

    /**
     * Processes a symbol that can denote a Markdown link (currently,
     * parentheses and brackets).
     *
     * @param k     the current position
     */
    private void manageLink(int k) {
        char c = paragraph.charAt(k);
        char match = matchingParenthesis(c);
        if (c == '(' || c == '[') {
            open.put(c + "", 1);
            contents.addLast(new StringBuilder(c + ""));
        } else if ((c == ')' || c == ']') && open.getOrDefault(match + "", 0) > 0) {
            collect(match + "", c + "", false);
            open.put(match + "", 0);
        } else {
            contents.addLast(new StringBuilder(c + ""));
        }
        if (contents.size() >= 2) {
            // If the last items in the list have the form [...](...), merge them into an HTML link
            StringBuilder b = contents.pollLast();
            StringBuilder a = contents.pollLast();
            if (a.charAt(0) == '[' && a.charAt(a.length() - 1) == ']' &&
                b.charAt(0) == '(' && b.charAt(b.length() - 1) == ')')
            {
                appendLink(b.substring(1, b.length() - 1), a.substring(1, a.length() - 1));
            } else {
                contents.addLast(a);
                contents.addLast(b);
            }
        }
    }

    /**
     * Processes a token identified as a possible closing tag
     * (may or may not be a valid closing tag, but is a
     * syntactically correct Markdown tag)
     *
     * @param current           current position
     * @param tag               Markdown tag that was encountered
     * @param lastWhitespace    whether last symbol was whitespace
     * @return                  offset by which index should be moved after exiting the function
     */
    private int manageClosingTag(int current, String tag, boolean lastWhitespace) {
        if (lastWhitespace) { // Leading whitespace => invalid closing tag
            contents.peekLast().append(tag);
        } else {
            if (open.getOrDefault("(", 0) > 0 && current < lastParenthesis) {
                // Inside a pair of parentheses
                collect(tag, tag, false);
            } else {
                collect(tag, tag, true);
            }
        }
        return tag.length();
    }

    /**
     * Processes a token identified as a possible opening tag
     * (may or may not be a valid opening tag, but is a
     * syntactically correct Markdown tag)
     *
     * @param current           current position
     * @param tag               Markdown tag that was encountered
     * @param nextWhitespace    whether next symbol is whitespace
     * @return                  offset by which index should be moved after exiting the function
     */
    private int manageOpeningTag(int current, String tag, boolean nextWhitespace) {
        if (nextWhitespace) { // Not valid opening tag
            contents.addLast(new StringBuilder(tag).append(paragraph.charAt(current + tag.length())));
            // Add the following space as well, not to confuse this token with an opening tag
            return tag.length() + 1;
        } else {
            contents.addLast(new StringBuilder(tag));
            open.put(tag, open.getOrDefault(tag, 0) + 1);
            return tag.length();
        }
    }

    /**
     * Appends the current paragraph, starting from index <code>l</code>,
     * to <code>global</code> after conversion to HTML
     *
     * @param l      the left bound of the interval
     */
    private void parseContents(int l) {
        int current = l;
        while (current < paragraph.length()) {
            char now = paragraph.charAt(current);
            if (now == '[' || now == ']' || now == '(' || now == ')') {
                manageLink(current++);
                continue;
            }
            char prev = (current != l) ? paragraph.charAt(current - 1) : (char)1;
            boolean lastWhitespace = (current != l && prev == ' ');
            String tag = getTag(current);
            if (tag == null) {
                contents.add(readChar(current));
                current += (paragraph.charAt(current) == '\\') ? 2 : 1;
            } else if (open.getOrDefault(tag, 0) > 0) {
                current += manageClosingTag(current, tag, lastWhitespace);
            } else {
                if (current + tag.length() >= paragraph.length()) { // Symbol after end of tag falls outside interval
                    contents.addLast(new StringBuilder(paragraph.substring(current)));
                } else {
                    boolean nextWhitespace = Character.isWhitespace(paragraph.charAt(current + tag.length()));
                    current += manageOpeningTag(current, tag, nextWhitespace);
                }
            }
        }
        clean();
    }

    /**
     * Parses current paragraph as a whole and stores the result in <code>global</code>.
     */
    private void parseParagraph() {
        int h = headerLevel();
        if (h == 0) {
            appendLiteralTag(global,"p", "", true);
            parseContents(0);
            appendLiteralTag(global, "p", "", false);
        } else {
            appendLiteralTag(global, "h" + h, "", true);
            parseContents(h + 1);
            appendLiteralTag(global, "h" + h, "", false);
        }
    }

    /**
     * Determines header level of the current paragraph.
     *
     * @return   the header level (1-6), or 0 if no header detected.
     */
    private int headerLevel() {
        int level = 0;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (c == '#') {
                level++;
                if (level > 6) { // Only 6 allowed header types in HTML
                    return 0;
                }
            } else if (c == ' ') { // Required header level confirmed with follow-up space
                return level;
            } else { // Unexpected symbol
                return 0;
            }
        }
        return 0;
    }
}

public class Md2Html {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Error launching Md2Html: Input and/or output file not provided.");
            return;
        }
        String inputFile = args[0];
        String outputFile = args[1];
        Converter converter = new Converter();
        // try-with-resources
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)))
        {
            System.out.println("Conversion started...");
            System.out.println();
            StringBuilder paragraph = new StringBuilder();
            boolean lastEmpty = false;
            while (true) {
                String line = in.readLine();
                if (line == null) { // end of file
                    break;
                }
                if (line.isEmpty()) {
                    if (lastEmpty) continue; // ignore two or more empty lines in a row
                    lastEmpty = true;
                    if (paragraph.length() > 0) { // so as not to parse empty paragraphs
                        out.write(converter.parseParagraph(paragraph.toString()));
                        out.write('\n');
                    }
                    paragraph = new StringBuilder();
                } else {
                    lastEmpty = false;
                    if (paragraph.length() > 0) {
                        paragraph.append('\n');
                    }
                    paragraph.append(line);
                }
            }
            if (paragraph.length() > 0) {
                out.write(converter.parseParagraph(paragraph.toString()));
            }
            System.out.println("Success: file " + inputFile + " converted to HTML and saved as " + outputFile + ".");
            System.out.println();
            System.out.println("Thank you for using Md2Html!");
        } catch (FileNotFoundException ex) {
            // Пункт 1. Очень жаль, вы проиграли.
            System.out.println("Error launching Md2Html (file not found: " + args[0] + ").");
        } catch (IOException ex) {
            // См. пункт 1.
            System.out.println("Input/output error: " + ex.getMessage() + ".");
        }
    }
}
