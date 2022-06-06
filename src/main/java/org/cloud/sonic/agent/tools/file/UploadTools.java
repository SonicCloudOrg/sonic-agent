/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.agent.tools.file;

import net.coobird.thumbnailator.Thumbnails;
import org.cloud.sonic.common.feign.FolderFeignClient;
import org.cloud.sonic.common.http.RespEnum;
import org.cloud.sonic.common.http.RespModel;
import org.cloud.sonic.common.tools.FileTool;
import org.cloud.sonic.common.tools.SpringTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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

    public static String upload(File uploadFile, String type) {
        if(uploadFile.isDirectory()){
            return null;
        }
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
                throw new RuntimeException("Thumbnails.of(uploadFile) failed: " + e.getMessage());
            }
            transfer = new File(folder + File.separator + timeMillis + "transfer.jpg");
        } else {
            transfer = uploadFile;
        }

        String resData = "upload failed, not url";
        try {
            MultipartFile multipartFile = FileTool.fileToMultipartFile("file", transfer);
            RespModel<String> respModel = SpringTool.getBean(FolderFeignClient.class).uploadFiles(multipartFile, type);
            if (respModel.getCode() != RespEnum.UPLOAD_OK.getCode()) {
                logger.error("upload failed: {}", respModel);
                throw new RuntimeException("upload failed:" + respModel);
            }
            resData = respModel.getData();
        } catch (Exception e) {
            logger.error("upload failed:", e);
            throw new RuntimeException("upload failed:" + e.getMessage());
        }finally {
            transfer.delete();
        }
        return resData;
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
                MultipartFile multipartFile = FileTool.fileToMultipartFile("file", branchFile);
                RespModel<String> respModel = SpringTool.getBean(FolderFeignClient.class)
                        .uploadRecord(multipartFile, uuid, i, num);
                if (respModel.getCode() != RespEnum.UPLOAD_OK.getCode()) {
                    logger.error("upload failed: {}", respModel);
                }
                url = respModel.getData();
                successNum++;
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
        } catch (IOException e) {
            logger.error("upload failed:", e);
        }
        return url;
    }
}
