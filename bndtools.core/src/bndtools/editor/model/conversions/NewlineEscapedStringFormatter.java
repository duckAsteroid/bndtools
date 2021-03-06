package bndtools.editor.model.conversions;

import bndtools.editor.model.BndEditModel;

public class NewlineEscapedStringFormatter implements Converter<String, String> {

    public String convert(String input) throws IllegalArgumentException {
        if(input == null)
            return null;

        // Shortcut the result for the majority of cases where there is no newline
        if(input.indexOf('\n') == -1)
            return input;

        // Build a new string with newlines escaped
        StringBuilder result = new StringBuilder();
        int position = 0;
        while(position < input.length()) {
            int newlineIndex = input.indexOf('\n', position);
            if(newlineIndex == -1) {
                result.append(input.substring(position));
                break;
            } else {
                result.append(input.substring(position, newlineIndex));
                result.append(BndEditModel.LINE_SEPARATOR);
                position = newlineIndex + 1;
            }
        }

        return result.toString();
    }


}
