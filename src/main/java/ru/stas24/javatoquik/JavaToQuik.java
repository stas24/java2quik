/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.stas24.javatoquik;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Stanislav Doroshin
 */
public abstract class JavaToQuik {
    
    private final String quikPath;
    private final String troName = "output.tro";
    private final String triName = "input.tri";
    private String account;
    private String clientCode;
    Thread watcherThread;
    PrintWriter output;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getClientCode() {
        return clientCode;
    }

    public void setClientCode(String clientCode) {
        this.clientCode = clientCode;
    }
    
    public JavaToQuik(String quikPath) throws IOException {
        this.quikPath = quikPath;
        
        // Delete old files
        try (FileWriter file = new FileWriter(quikPath + File.separator + troName)) {
            file.write("");
        }
        try (FileWriter file = new FileWriter(quikPath + File.separator + triName)) {
            file.write("");
        }
        readedLines = 0;
        
        // Input file (input for Quik)
        FileWriter fw = new FileWriter(quikPath + File.separator + triName, true);
        BufferedWriter bw = new BufferedWriter(fw);
        output = new PrintWriter(bw);
        
        // Output file (output from Quik)
        startWatcher();
    }
    
    abstract protected void onTransactionResult(Integer transactionId, Map args);
    abstract protected void onStop(String reason);
    
    // Количество прочитанных строк из файла troName, используется для чтения только новых строк
    private int readedLines;
    
    private void startWatcher() {
        // Запустить поток, который будет отслеживать изменения в файле troName
        
        watcherThread = new Thread(new Runnable() {
            
            @Override
            public void run() {
                
                try {
                    Path watchDir = Paths.get(quikPath);
                    WatchService watcher = watchDir.getFileSystem().newWatchService();
                    watchDir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

                    while (true) {
                        WatchKey watchKey = watcher.take();
                        List<WatchEvent<?>> events = watchKey.pollEvents();
                        for (WatchEvent event : events) {
                            // Отслеживается изменение только файла troName
                            if (troName.equals(event.context().toString())){
                                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                    System.out.println("Modify: " + event.context().toString());
                                    
                                    // Прочитать новые строчки
                                    // BufferedReader.readLine()
                                    LinkedList<String> lines = new LinkedList<>();
                                    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(quikPath + File.separator + event.context().toString()), StandardCharsets.UTF_8))) {
                                        String line;
                                        int lineIndex = 0;
                                        while ((line = bufferedReader.readLine()) != null) {
                                            if (lineIndex >= readedLines - 1) {
                                                lines.add(line);
                                            }
                                            lineIndex++;
                                        }
                                        readedLines = lineIndex;
                                    } catch (FileNotFoundException ex) {
                                        System.out.println("Error: " + ex.toString());
                                    }
                                    
                                    // TRANS_ID=14;STATUS=0;TRANS_NAME="Ввод заявки"; DESCRIPTION="Отправлена транзакция";
                                    // TRANS_ID=14;STATUS=3;TRANS_NAME="Ввод заявки"; DESCRIPTION="(160) Заявка на покупку N 68359610 зарегистрирована."; ORDER_NUMBER=68359610;
                                    for (String line: lines){
                                        Map<String, String> args = new HashMap<>();
                                        // Распарсить строчку
                                        // Сохранить все параметры в Map
                                        for (String keyValue: line.split(";")) {
                                            try {
                                                String[] pairs = keyValue.split("=", 2);
                                                args.put(pairs[0].trim(), pairs[1].trim());
                                            } catch (Exception ex) {
                                                
                                            }
                                        }
                                        
                                        Integer transactionId;
                                        try {
                                            transactionId = Integer.parseInt(args.get("TRANS_ID"));
                                        } catch (Exception ex) {
                                            transactionId = -1;
                                        }
                                        
                                        // Вызвать callback
                                        onTransactionResult(transactionId, args);
                                    }
                                }
                            }
                        }
                        boolean valid = watchKey.reset();
                        if (!valid) {
                            onStop("watckKey.reset() = false");
                            break;
                        }
                    }
                } catch (IOException ex) {
                    System.out.println("Error: " + ex.toString());
                    onStop(ex.toString());
                } catch (InterruptedException ex) {
                    System.out.println("Interrapted: " + ex.toString());
                    onStop(ex.toString());
                }
            }
        });
        watcherThread.start();
    }
    
    public void stop() {
        watcherThread.interrupt();
    }
    
    // Уникальный номер заявки, фактически - это количество отправленных заявок
    int transId = 0;
    protected final int sendTransaction(String transaction) {
        transId++;
        output.println("TRANS_ID=" + transId + "; " + transaction);
        output.flush();
        return transId;
    }
    
    public enum Operation {
        BUY('B'), SELL('S');
        
        private final char operationCode;
        Operation(char operationCode) {
            this.operationCode = operationCode;
        }
        public char getValue() {
            return operationCode;
        }
    }
    
    /*
    TYPE - Тип заявки, необязательный параметр. Значения: «L» – лимитированная (по умолчанию), «M» – рыночная
    OPERATION - Направление заявки, обязательный параметр. Значения: «S» – продать, «B» – купить
    Пример:
    ACCOUNT=4100WWQ; CLIENT_CODE=4100WWQ; TYPE=M; TRANS_ID=8; CLASSCODE=SPBFUT; SECCODE=SRM6; ACTION=NEW_ORDER; OPERATION=B; PRICE=16231; QUANTITY=15;
    */
    public int sendOrder(String classCode, String secCode, Operation operation, BigDecimal price, int quantity) {
        return sendTransaction("ACCOUNT=" + getAccount() +
                "; CLIENT_CODE=" + getClientCode() +
                "; TYPE=M; CLASSCODE=" + classCode +
                "; SECCODE=" + secCode +
                "; ACTION=NEW_ORDER; OPERATION=" + operation +
                "; PRICE=" + price.toPlainString() +
                "; QUANTITY=" + quantity + ";");
    }
    
    /*
    Снятие всех заявок
    Пример:
    TRANS_ID=1; CLASSCODE=TQBR; ACTION=KILL_ALL_ORDERS; CLIENT_CODE=Q6;
    */
    public int sendKillAllOrders(String classCode) {
        return sendTransaction("CLASSCODE=" + classCode + "; ACTION=KILL_ALL_ORDERS; CLIENT_CODE=" + getClientCode() + ";");
    }

    /*
    Перестановка заявок на срочном рынке FORTS
    Пример:
    ACTION=MOVE_ORDERS; TRANS_ID=333; CLASSCODE=SPBFUT; SECCODE=EBM6; FIRM_ID=SPBFUT389; MODE=1; FIRST_ORDER_NUMBER=21445064; FIRST_ORDER_NEW_PRICE=10004; FIRST_ORDER_NEW_QUANTITY=4; SECOND_ORDER_NUMBER=21445065; SECOND_ORDER_NEW_PRICE=10004; SECOND_ORDER_NEW_QUANTITY=4;
    */
    public int sendMoveOrders() {
        // TODO: Вставить параметры транзакции из аргументов метода
        return sendTransaction("ACTION=MOVE_ORDERS; CLASSCODE=SPBFUT; SECCODE=EBM6; FIRM_ID=SPBFUT389; MODE=1; FIRST_ORDER_NUMBER=21445064; FIRST_ORDER_NEW_PRICE=10004; FIRST_ORDER_NEW_QUANTITY=4; SECOND_ORDER_NUMBER=21445065; SECOND_ORDER_NEW_PRICE=10004; SECOND_ORDER_NEW_QUANTITY=4;");
    }
    
    public static void main(String args[]) throws IOException {
        JavaToQuik javaToQuik = new JavaToQuik("D:\\") {
            @Override
            protected void onTransactionResult(Integer transactionId, Map args) {
                System.out.println(transactionId + " " + args);
            }

            @Override
            protected void onStop(String reason) {
                System.out.println("onStop(" + reason + ")");
            }
        };
        javaToQuik.setAccount("4100WWQ");
        javaToQuik.setClientCode("4100WWQ");
        
        System.in.read();
        javaToQuik.sendOrder("SPBFUT", "SRM6", Operation.BUY, new BigDecimal("12680"), 1);
        //javaToQuik.sendTransaction("ACCOUNT=" + javaToQuik.getAccount() + "; CLIENT_CODE=" + javaToQuik.getClientCode() + "; TYPE=M; CLASSCODE=SPBFUT; SECCODE=SRM6; ACTION=NEW_ORDER; OPERATION=S; PRICE=16231; QUANTITY=1;");
        
        System.in.read();
        // Удалить все заявки
        //javaToQuik.sendTransaction("CLASSCODE=SPBFUT; ACTION=KILL_ALL_ORDERS; CLIENT_CODE=" + javaToQuik.getClientCode() + ";");
        javaToQuik.sendKillAllOrders("SPBFUT");
        System.in.read();
        javaToQuik.stop();
    }
}
