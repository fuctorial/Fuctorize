package ru.fuctorial.fuctorize.utils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PacketStructureAnalyst {

    public static class Segment {
        public String label;
        public byte[] data;
        public boolean isString;

        public Segment(String label, byte[] data, boolean isString) {
            this.label = label;
            this.data = data;
            this.isString = isString;
        }
    }

    public static List<Segment> analyze(byte[] packetData) {
        List<Segment> segments = new ArrayList<>();
        if (packetData == null || packetData.length == 0) return segments;

        int i = 0;
        int lastBinaryStart = 0;

        while (i < packetData.length) {
             
            int strLen = getStrictStringLength(packetData, i);

             
             
             
            if (strLen >= 4) {
                 
                if (i > lastBinaryStart) {
                    byte[] binData = copyRange(packetData, lastBinaryStart, i);
                    segments.add(new Segment("Raw (" + binData.length + "b)", binData, false));
                }

                 
                byte[] strData = copyRange(packetData, i, i + strLen);
                String decoded = new String(strData, StandardCharsets.UTF_8);
                segments.add(new Segment("Str: \"" + decoded + "\"", strData, true));

                i += strLen;
                lastBinaryStart = i;
            } else {
                i++;
            }
        }

         
        if (lastBinaryStart < packetData.length) {
            byte[] tail = copyRange(packetData, lastBinaryStart, packetData.length);
            segments.add(new Segment("Raw (" + tail.length + "b)", tail, false));
        }

        return segments;
    }

     
    private static int getStrictStringLength(byte[] data, int offset) {
        int len = 0;
        for (int i = offset; i < data.length; i++) {
            byte b = data[i];
            if (isStrictProtocolChar(b)) {
                len++;
            } else {
                break;
            }
        }
        return len;
    }

     
    private static boolean isStrictProtocolChar(byte b) {
         
        if (b >= 65 && b <= 90) return true;
         
        if (b >= 97 && b <= 122) return true;
         
        if (b >= 48 && b <= 57) return true;

         
         
         
        return b == 32 || b == 95 || b == 45 || b == 46 || b == 58 || b == 47 ||
                b == 123 || b == 125 || b == 34 || b == 44;
    }

    private static byte[] copyRange(byte[] src, int start, int end) {
        int len = end - start;
        byte[] dest = new byte[len];
        System.arraycopy(src, start, dest, 0, len);
        return dest;
    }
}