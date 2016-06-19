/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.stas24.dde;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static sun.security.krb5.Confounder.bytes;

/**
 *
 * @author Stanislav Doroshin
 */
public class XLTable implements Iterable<HashMap<String, Object>>{

    @Override
    public Iterator<HashMap<String, Object>> iterator() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public static class Type {
        public static short Unk    = 0xFF;
        public static short EOF    = 0xF0;
        public static short Table  = 0x10; // 4
        public static short Float  = 0x01; // 8
        public static short String = 0x02; // 1+N
        public static short Bool   = 0x03; // 2
        public static short Error  = 0x04; // 2
        public static short Blank  = 0x05; // 2
        public static short Int    = 0x06; // 2
        public static short Skip   = 0x07; // 2
    }
    
    DataInputStream dis;
    int rows;
    int cols;
    int type;
    public XLTable (InputStream is) throws IOException {
        dis = new DataInputStream(is);
        
        
        // read headers
        type = readUShort();
        int blockSize = readUShort();
        rows = readUShort();
        cols = readUShort();
        
        readAll();
    }

    public List<List<Object>> getMatrix() {
        return matrix;
    }
    
    int cellCounter = 0;
    int blockSize;
    List<Object> row;
    final List<List<Object>> matrix = new ArrayList<>();
    private void readAll() throws IOException {
        while (cellCounter < rows * cols) {
            // Прочитать тип колонки
            int columnType = readUShort();
            blockSize = readUShort(); // Количество байт для следующего блока
            
            if (columnType == Type.String) { // Строки
                while (blockSize > 0) {
                    // Получить длину строки
                    int stringLength = readByte();
                    // прочитать
                    addCell(readString(stringLength));
                }
            } else if (columnType == Type.Float) { // Число
                
            } else {
                // неизвестный тип
                System.out.println("Unknown type: " + columnType);
            }
        }
    }
    
    private void addCell(Object object) {
        cellCounter++;
        // Если строка вообще не создана или заполнена, то создать новую
        if (row == null || row.size() >= cols) {
            row = new ArrayList<>(cols);
            matrix.add(row);
        }
        row.add(object);
    }
    
    private String readString(int length) throws IOException {
        blockSize -= length;
        
        if (length == 0) {
            return "";
        } else {
            // Прочитать N байт в строку
            byte[] stringBuffer = new byte[length];
            dis.read(stringBuffer, 0, length);
            return new String(stringBuffer);
        }
    }
    
    private int readUShort() throws IOException {
        blockSize -= 2;
        ByteBuffer bb = ByteBuffer.allocate(2);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(dis.readByte());
        bb.put(dis.readByte());
        return bb.getShort(0);
    }
    
    private byte readByte() throws IOException {
        blockSize -= 1;
        return dis.readByte();
    }
}
