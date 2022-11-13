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
package org.cloud.sonic.agent.tests.ios.mjpeg;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * mjpeg input stream handler
 * {@link https://github.com/sarxos/webcam-capture/blob/master/webcam-capture/src/main/java/com/github/sarxos/webcam/util/MjpegInputStream.java}
 */
@Slf4j
public class MjpegInputStream extends DataInputStream {
    private final byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private final byte[] EOI_MARKER = {(byte) 0xFF, (byte) 0xD9};
    private final String CONTENT_LENGTH = "Content-Length".toLowerCase();
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 1024 * 5 + HEADER_MAX_LENGTH;

    public MjpegInputStream(final InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
    }

    private int getEndOfSequence(final DataInputStream in, final byte[] sequence) throws IOException {
        int s = 0;
        byte b;
        for (int i = 0; i < FRAME_MAX_LENGTH; i++) {
            b = (byte) in.readUnsignedByte();
            if (b == sequence[s]) {
                s++;
                if (s == sequence.length) {
                    return i + 1;
                }
            } else {
                s = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(final DataInputStream in, final byte[] sequence) throws IOException {
        int end = getEndOfSequence(in, sequence);
        return end < 0 ? -1 : end - sequence.length;
    }

    private int parseContentLength(final byte[] headerBytes) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(headerBytes);
        InputStreamReader inputStreamReader = new InputStreamReader(byteArrayInputStream);
        BufferedReader br = new BufferedReader(inputStreamReader);
        String line;
        int result = 0;
        while (true) {
            try {
                if ((line = br.readLine()) == null) break;
            } catch (IOException e) {
                log.info(e.getMessage());
                break;
            }
            if (line.toLowerCase().startsWith(CONTENT_LENGTH)) {
                final String[] parts = line.split(":");
                if (parts.length == 2) {
                    result = Integer.parseInt(parts[1].trim());
                    break;
                }
            }
        }
        try {
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            inputStreamReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            byteArrayInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    public ByteBuffer readFrameForByteBuffer() throws IOException {
        mark(FRAME_MAX_LENGTH);
        int n = getStartOfSequence(this, SOI_MARKER);
        reset();
        final byte[] header = new byte[n];
        readFully(header);
        int length;
        try {
            length = parseContentLength(header);
        } catch (NumberFormatException e) {
            length = getEndOfSequence(this, EOI_MARKER);
        }
        if (length == 0) {
            log.error("EOI Marker 0xFF,0xD9 not found!");
        }
        reset();
        final byte[] frame = new byte[length];
        skipBytes(n);
        readFully(frame);
        return ByteBuffer.wrap(frame);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

}