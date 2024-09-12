package org.cloud.sonic.agent.controller;

import org.cloud.sonic.agent.bridge.android.AndroidDeviceBridgeTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/file")
public class FileController {
    @GetMapping("/directory")
    public List<FileItem> directory(@RequestParam String id, @RequestParam(value = "path") String targetPath) {
        List<FileItem> directory = new ArrayList<>();
            String tree = AndroidDeviceBridgeTool.executeCommand(AndroidDeviceBridgeTool.getIDeviceByUdId(id), "ls -l "+targetPath);
            // 创建列表用于存储目录和文件信息
            // 将字符串按换行分割成行
            String[] lines = tree.split("\n");
            // 从第二行开始处理，因为第一行通常是总计信息（总大小等）
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                char typeChar = line.charAt(0); // 判断文件类型
                String type = (typeChar == '-') ? "file" : "directory";
                String[] parts = line.split("\\s+");
                String size = Objects.equals(type,"directory")?"": getSize(parts);
                String date = parts[5] + " " + parts[6];
                String label = parts[7];
                String path=targetPath+label;
                String permission=parts[0];
                double realSize=Objects.equals(type,"directory")?0: Long.parseLong(parts[4]);
                directory.add(new FileItem(label, type, size, date,path,permission,realSize));
            }
            directory.sort(Comparator.comparing(FileItem::getType) // 按类型，确保文件夹排在前面
                    .thenComparing(FileItem::getLabel, String.CASE_INSENSITIVE_ORDER)); // 按名称排序（不区分大小写）
        return directory;
    }
    @Data
    public static class FileItem {
        private String label;
        private String type;
        private String size;
        private double realSize;
        private String date;
        private  String path;
        private  String permission;
        public FileItem(String label, String type, String size, String date,String path,String permission,double realSize) {
            this.label = label;
            this.type = type;
            this.size = size;
            this.date = date;
            this.path = path;
            this.realSize=realSize;
            this.permission = permission;
        }
    }
    private static String getSize(String[] parts) {
        long sizeInBytes  = Long.parseLong(parts[4]);
        String size; // 文件大小字符串
        if (sizeInBytes < 1048576) { // 小于 1 MB
            double sizeInKB = sizeInBytes / 1024.0; // 转换为 KB
            size = String.format("%.2f KB", sizeInKB); // 格式化为 2 位小数的 KB
        } else {
            double sizeInMB = sizeInBytes / 1048576.0; // 转换为 MB
            size = String.format("%.2f MB", sizeInMB); // 格式化为 2 位小数的 MB
        }
        return size;
    }
}
