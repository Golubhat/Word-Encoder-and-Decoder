import tkinter as tk
from tkinter import filedialog, messagebox
import json
import csv
import struct
import os

BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"


def int_to_base62(n):
    if n == 0:
        return '0'
    s = ''
    while n > 0:
        s = BASE62[n % 62] + s
        n //= 62
    return s


def base62_to_int(s):
    n = 0
    for char in s:
        n = n * 62 + BASE62.index(char)
    return n


def is_word_char(c):
    return c.isalnum()


def save_dict_binary(filename, word_dict):
    with open(filename, 'wb') as f:
        for word, (val, freq) in word_dict.items():
            word_bytes = word.encode()
            f.write(struct.pack('I', len(word_bytes)))
            f.write(word_bytes)
            f.write(struct.pack('II', val, freq))


def load_dict_binary(filename):
    word_dict = {}
    with open(filename, 'rb') as f:
        while True:
            len_bytes = f.read(4)
            if not len_bytes:
                break
            length = struct.unpack('I', len_bytes)[0]
            word = f.read(length).decode()
            val, freq = struct.unpack('II', f.read(8))
            word_dict[word] = (val, freq)
    return word_dict


def export_json(filename, word_dict):
    metadata = {
        "total_words": len(word_dict),
        "encoding": "base62",
        "case_sensitive": True,
        "punctuation_included": True
    }
    data = [{"word": w, "value": v, "frequency": f}
            for w, (v, f) in word_dict.items()]
    with open(filename, 'w') as f:
        json.dump({"metadata": metadata, "dictionary": data}, f, indent=4)


def export_csv(filename, word_dict):
    with open(filename, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(["word", "value", "frequency"])
        for word, (val, freq) in word_dict.items():
            writer.writerow([word, val, freq])


def encode_file(input_path, output_path):
    # Load existing dictionary if available
    if os.path.exists("dictionary.bin"):
        word_dict = load_dict_binary("dictionary.bin")
    else:
        word_dict = {}

    # Find next available ID
    current_id = max((v for v, _ in word_dict.values()), default=0) + 1

    with open(input_path, 'r') as fin, open(output_path, 'w') as fout:
        content = fin.read()
        i = 0
        while i < len(content):
            if is_word_char(content[i]):
                start = i
                while i < len(content) and is_word_char(content[i]):
                    i += 1
                word = content[start:i]

                if word not in word_dict:
                    word_dict[word] = (current_id, 1)
                    current_id += 1
                else:
                    val, freq = word_dict[word]
                    word_dict[word] = (val, freq + 1)

                fout.write(int_to_base62(word_dict[word][0]))
            else:
                fout.write(content[i])
                i += 1

    # Save updated dictionary in all formats
    save_dict_binary("dictionary.bin", word_dict)
    export_json("dictionary.json", word_dict)
    export_csv("dictionary.csv", word_dict)

    messagebox.showinfo(
        "Success", f"Encoding complete!\nOutput: {output_path}")


def decode_file(encoded_path, output_path):
    word_dict = load_dict_binary("dictionary.bin")
    val_to_word = {v: w for w, (v, _) in word_dict.items()}
    with open(encoded_path, 'r') as fin, open(output_path, 'w') as fout:
        content = fin.read()
        i = 0
        while i < len(content):
            if content[i].isalnum():
                token = ''
                while i < len(content) and content[i].isalnum():
                    token += content[i]
                    i += 1
                val = base62_to_int(token)
                fout.write(val_to_word.get(val, '[?]'))
            else:
                fout.write(content[i])
                i += 1
    messagebox.showinfo(
        "Success", f"Decoding complete!\nOutput: {output_path}")

# GUI Setup


def browse_input_file(var):
    path = filedialog.askopenfilename(filetypes=[("Text Files", "*.txt")])
    if path:
        var.set(path)


def browse_output_file(var):
    path = filedialog.asksaveasfilename(
        defaultextension=".txt", filetypes=[("Text Files", "*.txt")])
    if path:
        var.set(path)


def run_encode():
    if not input_file.get() or not output_file.get():
        messagebox.showwarning(
            "Missing Info", "Please select both input and output files.")
        return
    encode_file(input_file.get(), output_file.get())


def run_decode():
    if not encoded_file.get() or not decoded_file.get():
        messagebox.showwarning(
            "Missing Info", "Please select both encoded and output files.")
        return
    decode_file(encoded_file.get(), decoded_file.get())


# Main Window
root = tk.Tk()
root.title("🧠 Word Encoder / Decoder (Base62)")
root.geometry("600x500")
root.resizable(False, False)

tk.Label(root, text="🔤 Encode a File", font=("Arial", 14, 'bold')).pack(pady=5)
input_file = tk.StringVar()
output_file = tk.StringVar()

tk.Frame(root, height=2, bd=1, relief='sunken').pack(fill='x', padx=10, pady=2)

tk.Label(root, text="Input File:").pack()
tk.Entry(root, textvariable=input_file, width=60).pack()
tk.Button(root, text="Browse", command=lambda: browse_input_file(
    input_file)).pack(pady=2)

tk.Label(root, text="Output File:").pack()
tk.Entry(root, textvariable=output_file, width=60).pack()
tk.Button(root, text="Browse", command=lambda: browse_output_file(
    output_file)).pack(pady=2)

tk.Button(root, text="🚀 Encode", command=run_encode,
          bg="#4CAF50", fg="white").pack(pady=10)

tk.Label(root, text="🔁 Decode a File", font=("Arial", 14, 'bold')).pack(pady=5)
encoded_file = tk.StringVar()
decoded_file = tk.StringVar()

tk.Label(root, text="Encoded File:").pack()
tk.Entry(root, textvariable=encoded_file, width=60).pack()
tk.Button(root, text="Browse", command=lambda: browse_input_file(
    encoded_file)).pack(pady=2)

tk.Label(root, text="Reconstructed Output:").pack()
tk.Entry(root, textvariable=decoded_file, width=60).pack()
tk.Button(root, text="Browse", command=lambda: browse_output_file(
    decoded_file)).pack(pady=2)

tk.Button(root, text="🔓 Decode", command=run_decode,
          bg="#2196F3", fg="white").pack(pady=10)

root.mainloop()
