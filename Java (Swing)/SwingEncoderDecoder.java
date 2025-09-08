import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;

public class SwingEncoderDecoder extends JFrame {

    JTextField inputFileField, outputFileField;
    JButton browseInput, browseOutput, encodeButton, decodeButton;

    Dictionary dictionary = new Dictionary();

    public SwingEncoderDecoder() {
        setTitle("Text Encoder/Decoder - Base62");
        setSize(600, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 3, 5, 5));

        inputFileField = new JTextField();
        outputFileField = new JTextField();
        browseInput = new JButton("Browse Input");
        browseOutput = new JButton("Browse Output");
        encodeButton = new JButton("Encode");
        decodeButton = new JButton("Decode");

        add(new JLabel("Input File:"));
        add(inputFileField);
        add(browseInput);

        add(new JLabel("Output File:"));
        add(outputFileField);
        add(browseOutput);

        add(new JLabel());
        add(encodeButton);
        add(decodeButton);

        browseInput.addActionListener(e -> chooseFile(inputFileField));
        browseOutput.addActionListener(e -> chooseFile(outputFileField));
        encodeButton.addActionListener(e -> handleEncode());
        decodeButton.addActionListener(e -> handleDecode());

        setVisible(true);
    }

    private void chooseFile(JTextField field) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void handleEncode() {
        try {
            Encoder encoder = new Encoder();

            // ✅ Load existing dictionary if file exists
            File dictFile = new File("dictionary.bin");
            if (dictFile.exists()) {
                dictionary.loadBinary("dictionary.bin");
            }

            encoder.encode(inputFileField.getText(), outputFileField.getText(), dictionary);
            JOptionPane.showMessageDialog(this, "Encoding complete. Dictionary updated and exported.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error during encoding: " + ex.getMessage());
        }
    }

    private void handleDecode() {
        try {
            Decoder decoder = new Decoder();
            dictionary.loadBinary("dictionary.bin");
            decoder.decode(inputFileField.getText(), outputFileField.getText(), dictionary);
            JOptionPane.showMessageDialog(this, "Decoding complete.");
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error during decoding: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        new SwingEncoderDecoder();
    }

    // === Utility Classes ===

    static class WordEntry {
        String word;
        int value;
        int frequency;

        WordEntry(String word, int value, int frequency) {
            this.word = word;
            this.value = value;
            this.frequency = frequency;
        }
    }

    static class Base62Util {
        static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

        static String intToBase62(int val) {
            if (val == 0)
                return "0";
            StringBuilder sb = new StringBuilder();
            while (val > 0) {
                sb.insert(0, BASE62.charAt(val % 62));
                val /= 62;
            }
            return sb.toString();
        }

        static int base62ToInt(String str) {
            int val = 0;
            for (char c : str.toCharArray()) {
                val = val * 62 + BASE62.indexOf(c);
            }
            return val;
        }
    }

    static class Dictionary {
        Map<String, WordEntry> map = new HashMap<>();
        java.util.List<WordEntry> entries = new ArrayList<>();
        int nextVal = 1;

        void addWord(String word) {
            if (!map.containsKey(word)) {
                WordEntry e = new WordEntry(word, nextVal++, 1);
                map.put(word, e);
                entries.add(e);
            } else {
                map.get(word).frequency++;
            }
        }

        int getValue(String word) {
            return map.containsKey(word) ? map.get(word).value : -1;
        }

        void updateNextVal() {
            int max = 0;
            for (WordEntry e : entries) {
                if (e.value > max)
                    max = e.value;
            }
            nextVal = max + 1;
        }

        String getWordByValue(int val) {
            for (WordEntry e : entries) {
                if (e.value == val)
                    return e.word;
            }
            return "[?]";
        }

        void saveBinary(String filename) throws IOException {
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(filename))) {
                for (WordEntry e : entries) {
                    out.writeInt(e.word.length());
                    out.writeBytes(e.word);
                    out.writeInt(e.value);
                    out.writeInt(e.frequency);
                }
            }
        }

        void loadBinary(String filename) throws IOException {
            map.clear();
            entries.clear();
            try (DataInputStream in = new DataInputStream(new FileInputStream(filename))) {
                while (in.available() > 0) {
                    int len = in.readInt();
                    byte[] wordBytes = new byte[len];
                    in.readFully(wordBytes);
                    String word = new String(wordBytes);
                    int value = in.readInt();
                    int freq = in.readInt();
                    WordEntry entry = new WordEntry(word, value, freq);
                    map.put(word, entry);
                    entries.add(entry);
                }
            }
            updateNextVal(); // ✅ reset nextVal correctly
        }

        void exportCSV(String filename) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                writer.write("word,value,frequency\n");
                for (WordEntry e : entries) {
                    writer.write(String.format("\"%s\",%d,%d\n", e.word, e.value, e.frequency));
                }
            }
        }

        void exportJSON(String filename) throws IOException {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
                writer.write("{\n  \"metadata\": {\n");
                writer.write("    \"total_words\": " + entries.size() + ",\n");
                writer.write("    \"encoding\": \"base62\",\n");
                writer.write("    \"case_sensitive\": true\n");
                writer.write("  },\n  \"dictionary\": [\n");
                for (int i = 0; i < entries.size(); i++) {
                    WordEntry e = entries.get(i);
                    writer.write(String.format("    {\"word\": \"%s\", \"value\": %d, \"frequency\": %d}", e.word,
                            e.value, e.frequency));
                    if (i < entries.size() - 1)
                        writer.write(",");
                    writer.write("\n");
                }
                writer.write("  ]\n}\n");
            }
        }
    }

    static class Encoder {
        boolean isWordChar(char c) {
            return Character.isLetterOrDigit(c);
        }

        void encode(String input, String output, Dictionary dict) throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(input));
                    BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {

                int ch;
                StringBuilder word = new StringBuilder();

                while ((ch = reader.read()) != -1) {
                    char c = (char) ch;
                    if (isWordChar(c)) {
                        word.append(c);
                    } else {
                        if (word.length() > 0) {
                            String w = word.toString();
                            dict.addWord(w);
                            writer.write(Base62Util.intToBase62(dict.getValue(w)));
                            word.setLength(0);
                        }
                        writer.write(c);
                    }
                }
                if (word.length() > 0) {
                    String w = word.toString();
                    dict.addWord(w);
                    writer.write(Base62Util.intToBase62(dict.getValue(w)));
                }
            }
            dict.saveBinary("dictionary.bin");
            dict.exportCSV("dictionary.csv");
            dict.exportJSON("dictionary.json");
        }
    }

    static class Decoder {
        boolean isBase62Char(char c) {
            return Character.isLetterOrDigit(c);
        }

        void decode(String input, String output, Dictionary dict) throws IOException {
            try (BufferedReader reader = new BufferedReader(new FileReader(input));
                    BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {

                int ch;
                StringBuilder token = new StringBuilder();

                while ((ch = reader.read()) != -1) {
                    char c = (char) ch;
                    if (isBase62Char(c)) {
                        token.append(c);
                    } else {
                        if (token.length() > 0) {
                            int val = Base62Util.base62ToInt(token.toString());
                            writer.write(dict.getWordByValue(val));
                            token.setLength(0);
                        }
                        writer.write(c);
                    }
                }
                if (token.length() > 0) {
                    int val = Base62Util.base62ToInt(token.toString());
                    writer.write(dict.getWordByValue(val));
                }
            }
        }
    }
}
