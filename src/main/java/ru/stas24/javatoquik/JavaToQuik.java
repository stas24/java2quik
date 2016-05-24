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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Stanislav Doroshin
 */
public abstract class JavaToQuik {
    
    private final String quikPath;
    private final String troName = "output.tro";
    private final String triName = "input.tri";
    Thread watcherThread;
    PrintWriter output;
    
    public JavaToQuik(String quikPath) throws IOException {
        this.quikPath = quikPath;
        
        // Delete old files
        try (FileWriter file = new FileWriter(quikPath + File.separator + troName)) {
            file.write("");
        }
        try (FileWriter file = new FileWriter(quikPath + File.separator + triName)) {
            file.write("");
        }
        
        // Input file (input for Quik)
        FileWriter fw = new FileWriter(quikPath + File.separator + triName, true);
        BufferedWriter bw = new BufferedWriter(fw);
        output = new PrintWriter(bw);
        
        // Output file (output from Quik)
        startWatcher();
    }
    
    abstract protected void onTransactionResult(Integer transactionId, Map args);
    abstract protected void onStop(String reason);
    
    private void startWatcher() {
        // Запустить поток, который будет отслеживать изменения в файле troName
        
        watcherThread = new Thread(new Runnable() {
            
            private final Set<Integer> processedTransactions = new HashSet<>();
            
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
                                        while ((line = bufferedReader.readLine()) != null) {
                                            lines.add(line);
                                        }
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
                                        
                                        // Вызвать общий callback только еслии такой заявки еще не было
                                        if ( ! processedTransactions.contains(transactionId)) {
                                            onTransactionResult(transactionId, args);
                                            processedTransactions.add(transactionId);
                                        } else {
                                            System.out.println("Transaction already processed: " + transactionId + " " + args.get("TRANS_ID"));
                                        }
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
    
    public void sendTransaction(String transaction) {
        output.println(transaction);
        output.flush();
    }
    
    public void stop() {
        watcherThread.interrupt();
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
        System.in.read();
        javaToQuik.sendTransaction("ACCOUNT=4100WWQ; CLIENT_CODE=4100WWQ; TYPE=M; TRANS_ID=8; CLASSCODE=SPBFUT; SECCODE=SRM6; ACTION=NEW_ORDER; OPERATION=S; PRICE=16231; QUANTITY=15;");
        
        System.in.read();
        // Удалить все заявки
        javaToQuik.sendTransaction("TRANS_ID=4; CLASSCODE=SPBFUT; ACTION=KILL_ALL_ORDERS; CLIENT_CODE=4100WWQ;");
        System.in.read();
        javaToQuik.stop();
    }
}
