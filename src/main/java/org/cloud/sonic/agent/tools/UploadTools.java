package org.cloud.sonic.agent.tools;

import com.alibaba.fastjson.JSONObject;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Calendar;
import java.util.UUID;

/**
 * @author ZhouYiXun
 * @des 所有上传方法在这里
 * @date 2021/8/17 23:38
 */
@Component
public class UploadTools {
    private final static Logger logger = LoggerFactory.getLogger(UploadTools.class);
    @Value("${sonic.server.host}")
    private String host;
    @Value("${sonic.server.folder-port}")
    private String port;
    private static String baseUrl;

    private static RestTemplate restTemplate;

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        UploadTools.restTemplate = restTemplate;
        baseUrl = "http://" + host + ":" + port + "/api/folder";
    }

    public static String upload(File uploadFile, String type) {
        File folder = new File("test-output");
        if (!folder.exists()) {//判断文件目录是否存在
            folder.mkdirs();
        }
        File transfer;
        if (type.equals("keepFiles") || type.equals("imageFiles")) {
            long timeMillis = Calendar.getInstance().getTimeInMillis();
            try {
                Thumbnails.of(uploadFile)
                        .scale(1f)
                        .outputQuality(0.25f).toFile(folder + File.separator + timeMillis + "transfer.jpg");
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
            transfer = new File(folder + File.separator + timeMillis + "transfer.jpg");
        } else {
            transfer = uploadFile;
        }
        FileSystemResource resource = new FileSystemResource(transfer);
        MultiValueMap<String, Object> param = new LinkedMultiValueMap<>();
        param.add("file", resource);
        param.add("type", type);
        ResponseEntity<JSONObject> responseEntity =
                restTemplate.postForEntity(baseUrl + "/upload", param, JSONObject.class);
        if (responseEntity.getBody().getInteger("code") == 2000) {
            transfer.delete();
        } else {
            logger.info("发送失败！" + responseEntity.getBody());
        }
        return responseEntity.getBody().getString("data");
    }

    public static String uploadPatchRecord(File uploadFile) {
        if (uploadFile.length() == 0) {
            uploadFile.delete();
            return null;
        }
        String url = "";
        long size = 1024 * 1024;
        int num = (int) (Math.ceil(uploadFile.length() * 1.0 / size));
        String uuid = UUID.randomUUID().toString();
        File file = new File("test-output/record" + File.separator + uuid);
        if (!file.exists()) {
            file.mkdirs();
        }
        try {
            RandomAccessFile before = new RandomAccessFile(uploadFile, "r");
            long beforeSize = uploadFile.length();
            byte[] bytes = new byte[1024];
            int len;
            int successNum = 0;
            for (int i = 0; i < num; i++) {
                File branchFile = new File(file.getPath() + File.separator + uploadFile.getName());
                RandomAccessFile branch = new RandomAccessFile(branchFile, "rw");
                while ((len = before.read(bytes)) != -1) {
                    if (beforeSize > len) {
                        branch.write(bytes, 0, len);
                        beforeSize -= len;
                    } else {
                        branch.write(bytes, 0, (int) beforeSize);
                    }
                    if (branch.length() >= size)
                        break;
                }
                branch.close();
                FileSystemResource resource = new FileSystemResource(branchFile);
                MultiValueMap<String, Object> param = new LinkedMultiValueMap<>();
                param.add("file", resource);
                param.add("uuid", uuid);
                param.add("index", i + "");
                param.add("total", num + "");
                ResponseEntity<JSONObject> responseEntity = restTemplate.postForEntity(baseUrl + "/upload/recordFiles", param, JSONObject.class);
                if (responseEntity.getBody().getInteger("code") == 2000) {
                    successNum++;
                }
                if (responseEntity.getBody().getString("data") != null) {
                    url = responseEntity.getBody().getString("data");
                }
                branchFile.delete();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            before.close();
            file.delete();
            if (successNum == num) {
                uploadFile.delete();
            } else {
                logger.info("上传缺失！");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return url;
    }
}
