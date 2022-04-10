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
package org.cloud.sonic.agent.tools.cv;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.opencv_core.*;
import org.cloud.sonic.agent.automation.FindResult;
import org.cloud.sonic.agent.tools.file.UploadTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.ThreadLocalRandom;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class TemMatcher {
    private final Logger logger = LoggerFactory.getLogger(TemMatcher.class);

    public FindResult getTemMatchResult(File temFile, File beforeFile) throws IOException {
        try {
            Mat sourceColor = imread(beforeFile.getAbsolutePath());
            Mat sourceGrey = new Mat(sourceColor.size(), CV_8UC1);
            cvtColor(sourceColor, sourceGrey, COLOR_BGR2GRAY);
            Mat template = imread(temFile.getAbsolutePath(), IMREAD_GRAYSCALE);
            Size size = new Size(sourceGrey.cols() - template.cols() + 1, sourceGrey.rows() - template.rows() + 1);
            Mat result = new Mat(size, CV_32FC1);

            long start = System.currentTimeMillis();
            matchTemplate(sourceGrey, template, result, TM_CCORR_NORMED);
            DoublePointer minVal = new DoublePointer();
            DoublePointer maxVal = new DoublePointer();
            Point min = new Point();
            Point max = new Point();
            minMaxLoc(result, minVal, maxVal, min, max, null);
            rectangle(sourceColor, new Rect(max.x(), max.y(), template.cols(), template.rows()), randColor(), 2, 0, 0);
            FindResult findResult = new FindResult();
            findResult.setTime((int) (System.currentTimeMillis() - start));
            long time = Calendar.getInstance().getTimeInMillis();
            String fileName = "test-output" + File.separator + time + ".jpg";
            imwrite(fileName, sourceColor);
            findResult.setX(max.x() + template.cols() / 2);
            findResult.setY(max.y() + template.rows() / 2);
            findResult.setUrl(UploadTools.upload(new File(fileName), "imageFiles"));
            return findResult;
        } finally {
            temFile.delete();
            beforeFile.delete();
        }
    }

    public static Scalar randColor() {
        int b, g, r;
        b = ThreadLocalRandom.current().nextInt(0, 255 + 1);
        g = ThreadLocalRandom.current().nextInt(0, 255 + 1);
        r = ThreadLocalRandom.current().nextInt(0, 255 + 1);
        return new Scalar(b, g, r, 0);
    }
}