#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#define MAX_WORDS 5000
#define MAX_WORD_LEN 100

// Base62 character set (0-9, A-Z, a-z)
char base62_chars[] = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

// Dictionary entry structure
typedef struct
{
    char *word;    // The word itself
    int value;     // Unique numeric value
    int frequency; // Count of word occurrence
} WordEntry;

/* ======================= Utility Functions ========================== */

// Convert integer to base62 string
void int_to_base62(int val, char *out)
{
    char buffer[10];
    int i = 0;
    if (val == 0)
    {
        strcpy(out, "0");
        return;
    }
    while (val > 0)
    {
        buffer[i++] = base62_chars[val % 62];
        val /= 62;
    }
    for (int j = 0; j < i; j++)
        out[j] = buffer[i - j - 1];
    out[i] = '\0';
}

// Convert base62 string to integer
int base62_to_int(const char *str)
{
    int val = 0;
    for (int i = 0; str[i]; i++)
    {
        char *ptr = strchr(base62_chars, str[i]);
        if (ptr)
            val = val * 62 + (ptr - base62_chars);
    }
    return val;
}

// Check if a character is part of a word (alphanumeric)
int is_word_char(int ch)
{
    return isalnum(ch);
}

// Find word index in dictionary (case-sensitive)
int find_word(WordEntry words[], int count, const char *word)
{
    for (int i = 0; i < count; i++)
    {
        if (strcmp(words[i].word, word) == 0)
            return i;
    }
    return -1;
}

/* ======================= File Operations ========================== */

// Write dictionary to binary file
void write_binary_dictionary(const char *filename, WordEntry words[], int count)
{
    FILE *file = fopen(filename, "wb");
    for (int i = 0; i < count; i++)
    {
        int len = strlen(words[i].word);
        fwrite(&len, sizeof(int), 1, file);
        fwrite(words[i].word, sizeof(char), len, file);
        fwrite(&words[i].value, sizeof(int), 1, file);
        fwrite(&words[i].frequency, sizeof(int), 1, file);
    }
    fclose(file);
}

// Read dictionary from binary file
int read_binary_dictionary(const char *filename, WordEntry words[])
{
    FILE *file = fopen(filename, "rb");
    if (!file)
        return 0;
    int count = 0;
    while (!feof(file))
    {
        int len;
        if (fread(&len, sizeof(int), 1, file) != 1)
            break;
        words[count].word = malloc(len + 1);
        fread(words[count].word, sizeof(char), len, file);
        words[count].word[len] = '\0';
        fread(&words[count].value, sizeof(int), 1, file);
        fread(&words[count].frequency, sizeof(int), 1, file);
        count++;
    }
    fclose(file);
    return count;
}

// Export dictionary to JSON
void export_dictionary_to_json(const char *filename, WordEntry words[], int count)
{
    FILE *f = fopen(filename, "w");
    fprintf(f, "{\n  \"metadata\": {\n");
    fprintf(f, "    \"total_words\": %d,\n", count);
    fprintf(f, "    \"encoding\": \"base62\",\n");
    fprintf(f, "    \"case_sensitive\": true,\n");
    fprintf(f, "    \"punctuation_included\": true\n");
    fprintf(f, "  },\n  \"dictionary\": [\n");

    for (int i = 0; i < count; i++)
    {
        fprintf(f, "    { \"word\": \"%s\", \"value\": %d, \"frequency\": %d }%s\n",
                words[i].word, words[i].value, words[i].frequency, (i < count - 1 ? "," : ""));
    }
    fprintf(f, "  ]\n}\n");
    fclose(f);
}

// Export dictionary to CSV
void export_dictionary_to_csv(const char *filename, WordEntry words[], int count)
{
    FILE *f = fopen(filename, "w");
    fprintf(f, "word,value,frequency\n");
    for (int i = 0; i < count; i++)
    {
        fprintf(f, "\"%s\",%d,%d\n", words[i].word, words[i].value, words[i].frequency);
    }
    fclose(f);
}

// Free dictionary memory
void free_dictionary(WordEntry words[], int count)
{
    for (int i = 0; i < count; i++)
        free(words[i].word);
}

// Find word by value
char *get_word_by_value(WordEntry words[], int count, int value)
{
    for (int i = 0; i < count; i++)
    {
        if (words[i].value == value)
            return words[i].word;
    }
    return NULL;
}

int next_value(WordEntry words[], int count)
{
    int max = 0;
    for (int i = 0; i < count; i++)
    {
        if (words[i].value > max)
            max = words[i].value;
    }
    return max + 1;
}

/* ======================= Main Conversion Logic ========================== */

// Input ➝ Encoded Output
int convert_input_to_output(const char *inputName, const char *outputName)
{
    FILE *input = fopen(inputName, "r");
    FILE *output = fopen(outputName, "w");
    WordEntry words[MAX_WORDS];
    int wordCount = read_binary_dictionary("dictionary.bin", words);
    char buffer[MAX_WORD_LEN];
    int ch, i;

    while ((ch = fgetc(input)) != EOF)
    {
        if (is_word_char(ch))
        {
            i = 0;
            do
            {
                buffer[i++] = ch;
                ch = fgetc(input);
            } while (is_word_char(ch) && i < MAX_WORD_LEN - 1);
            buffer[i] = '\0';

            int idx = find_word(words, wordCount, buffer);
            if (idx == -1)
            {
                words[wordCount].word = strdup(buffer);
                words[wordCount].value = next_value(words, wordCount); // ✅ new unique ID
                words[wordCount].frequency = 1;
                idx = wordCount++;
            }
            else
            {
                words[idx].frequency++;
            }
            char encoded[10];
            int_to_base62(words[idx].value, encoded);
            fputs(encoded, output);
            if (ch != EOF)
                fputc(ch, output);
        }
        else
        {
            fputc(ch, output);
        }
    }

    fclose(input);
    fclose(output);
    write_binary_dictionary("dictionary.bin", words, wordCount);
    export_dictionary_to_json("dictionary.json", words, wordCount);
    export_dictionary_to_csv("dictionary.csv", words, wordCount);
    free_dictionary(words, wordCount);

    printf("Input converted to output. Dictionary exported.\n");
    return 0;
}

// Encoded Output ➝ Reconstructed Input
int convert_output_to_input(const char *outputName, const char *reconName)
{
    FILE *input = fopen(outputName, "r");
    FILE *output = fopen(reconName, "w");
    WordEntry words[MAX_WORDS];
    int wordCount = read_binary_dictionary("dictionary.bin", words);
    char token[10];
    int ch;

    while ((ch = fgetc(input)) != EOF)
    {
        if (isalnum(ch))
        {
            int i = 0;
            token[i++] = ch;
            while (isalnum(ch = fgetc(input)) && i < 9)
                token[i++] = ch;
            token[i] = '\0';

            int val = base62_to_int(token);
            char *word = get_word_by_value(words, wordCount, val);
            if (word)
                fputs(word, output);
            else
                fprintf(output, "[%s]", token);

            if (ch != EOF)
                fputc(ch, output);
        }
        else
        {
            fputc(ch, output);
        }
    }

    fclose(input);
    fclose(output);
    free_dictionary(words, wordCount);

    printf("Output decoded to reconstructed input.\n");
    return 0;
}

/* ======================= Entry Point ========================== */

int main()
{
    int choice;
    char inputName[256], outputName[256];
    printf("\nWord Encoder/Decoder Menu\n");
    printf("1. Convert Input -> Encoded Output\n");
    printf("2. Convert Output -> Reconstructed Input\n");
    printf("Enter your choice: ");
    scanf("%d", &choice);
    getchar();

    if (choice == 1)
    {
        printf("Enter input file name: ");
        fgets(inputName, 256, stdin);
        inputName[strcspn(inputName, "\n")] = 0;
        printf("Enter output file name: ");
        fgets(outputName, 256, stdin);
        outputName[strcspn(outputName, "\n")] = 0;
        convert_input_to_output(inputName, outputName);
    }
    else if (choice == 2)
    {
        printf("Enter encoded file name: ");
        fgets(outputName, 256, stdin);
        outputName[strcspn(outputName, "\n")] = 0;
        printf("Enter reconstructed file name: ");
        fgets(inputName, 256, stdin);
        inputName[strcspn(inputName, "\n")] = 0;
        convert_output_to_input(outputName, inputName);
    }
    else
    {
        printf("Invalid choice.\n");
    }

    return 0;
}
