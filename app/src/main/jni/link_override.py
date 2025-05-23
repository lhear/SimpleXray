import os
import sys
import argparse

def process_single_file(source_file_path):
    """
    Reads the first line of the source file. If it's a valid path to another file,
    replaces the source file's content with the target file's content.

    Args:
        source_file_path (str): The path to the source file to process.
    """
    # print(f"正在处理文件: {source_file_path}") # 递归时可能输出过多，可以选择注释或调整

    try:
        # 1. 读取源文件的第一行，作为潜在的目标文件路径
        # 使用 'r' 模式，如果文件不存在会抛出 FileNotFoundError (尽管os.walk找到的文件通常存在)
        # 使用 utf-8 编码，如果编码不匹配可能抛出 UnicodeDecodeError
        with open(source_file_path, 'r', encoding='utf-8') as f:
            potential_target_path_str = f.readline().strip()

        if not potential_target_path_str:
            # 文件为空或第一行为空，不包含目标路径
            # print(f"  跳过 '{source_file_path}'：文件为空或第一行为空。") # 同样可能输出过多
            return # 静默跳过空文件或第一行为空的

        # 2. 解析目标文件路径
        # 目标路径是相对于源文件所在的目录
        source_dir = os.path.dirname(os.path.abspath(source_file_path))
        # 合并源文件目录和潜在的相对路径，然后规范化（处理'.'和'..'）
        target_file_path = os.path.abspath(os.path.join(source_dir, potential_target_path_str))

        # 3. 检查目标文件是否存在并且是一个文件
        if not os.path.exists(target_file_path):
            print(f"  跳过 '{source_file_path}'：目标文件 '{target_file_path}' 不存在。")
            return
        if not os.path.isfile(target_file_path):
            print(f"  跳过 '{source_file_path}'：目标路径 '{target_file_path}' 不是一个文件。")
            return

        # 4. 检查源文件和目标文件是否是同一个文件，避免无限循环或不必要的读写
        # 使用 os.path.samefile 来比较，更可靠
        if os.path.samefile(source_file_path, target_file_path):
            # print(f"  跳过 '{source_file_path}'：源文件和目标文件相同。") # 同样可能输出过多
            return # 静默跳过自引用的文件

        # 5. 读取目标文件的内容
        with open(target_file_path, 'r', encoding='utf-8') as f_target:
            target_content = f_target.read()

        # 6. 将目标文件的内容写入源文件，覆盖原有内容
        with open(source_file_path, 'w', encoding='utf-8') as f_source:
            f_source.write(target_content)

        print(f"  成功：已用目标文件 '{target_file_path}' 的内容覆盖 '{source_file_path}'。")

    except FileNotFoundError:
        # os.walk 找到的文件基本不会是 FileNotFoundError，除非处理过程中被删除。
        # 但目标文件可能不存在，这个在前面检查了。这个except更多是作为潜在的兜底。
        print(f"  错误：文件未找到（处理 '{source_file_path}' 时）。")
    except IsADirectoryError:
         print(f"  错误：尝试将目录作为文件打开（在处理 '{source_file_path}' 时，尽管 os.walk 应该只提供文件路径）。")
    except UnicodeDecodeError:
        print(f"  错误：无法使用 utf-8 编码读取文件 '{source_file_path}' 或其目标。它可能是二进制文件。")
    except Exception as e:
        # 捕获其他可能的错误，如权限问题等
        print(f"  处理文件 '{source_file_path}' 时发生未知错误：{e}")


def process_directory_recursively(top_directory_path):
    """
    Recursively walks through all directories and files starting from top_directory_path
    and processes each file found.

    Args:
        top_directory_path (str): The starting directory for the recursive walk.
    """
    if not os.path.isdir(top_directory_path):
        print(f"错误：指定的路径 '{top_directory_path}' 不是一个有效的目录。")
        return

    print(f"开始递归处理目录及其子目录下的文件，起始路径: {top_directory_path}")
    processed_files_count = 0 # 统计 os.walk 找到的文件总数，不代表成功处理数

    try:
        # os.walk 会从 top_directory_path 开始，遍历其自身以及所有子目录
        # 对于访问到的每一个目录，它会返回 (当前目录路径, 子目录列表, 文件列表)
        for root, dirs, files in os.walk(top_directory_path):
            # root 是当前正在访问的目录的路径 (字符串)
            # dirs 是 root 目录下的子目录名称列表 (字符串列表)
            # files 是 root 目录下的非目录文件名称列表 (字符串列表)

            # 处理当前目录下的所有文件
            # print(f"  进入目录: {root}") # 可以选择打印当前正在处理的目录

            for file_name in files:
                file_path = os.path.join(root, file_name) # 构建文件的完整路径
                # 调用处理单个文件的函数
                process_single_file(file_path)
                processed_files_count += 1 # 统计找到的文件总数

            # os.walk 会自动进入 dirs 列表中的子目录，无需在此代码中手动处理目录进入。

    except Exception as e:
        print(f"遍历目录树 '{top_directory_path}' 时发生错误：{e}")

    print(f"目录树处理完成。在 '{top_directory_path}' 下共找到了 {processed_files_count} 个文件并尝试处理。")


if __name__ == "__main__":
    # 使用 argparse 来处理命令行参数
    parser = argparse.ArgumentParser(
        description='递归处理指定目录及其子目录下的所有文件。根据文件的第一行内容（作为相对路径）替换文件内容。'
    )
    parser.add_argument(
        'directory',
        help='需要处理的起始目录路径。脚本将递归检查该目录及其所有子目录下的文件。'
    )

    args = parser.parse_args()

    # 获取用户输入的目录路径
    directory_to_process = args.directory

    # 调用处理目录的函数 (现在是递归版本)
    process_directory_recursively(directory_to_process)