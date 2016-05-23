/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.stas24.javatoquik;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

/**
 *
 * @author Stanislav Doroshin
 */
public class JavaToQuik {
    
    private final String quikPath;
    
    public JavaToQuik(String quikPath)
    {
        this.quikPath = quikPath;
        
        // Open input file
        
        // Open output file
        
        
        
        startWatcher();
    }
    
    private final String troName = "output.tro";
    private final String triName = "input.tri";
    Thread watcherThread;
    
    private void startWatcher()
    {
        // Запустить поток, который будет отслеживать изменения в файле troName
        
        watcherThread = new Thread(new Runnable() {
            @Override
            public void run() {
                
                Path watchDir = Paths.get(quikPath);
                try {
                    WatchService watcher = watchDir.getFileSystem().newWatchService();
                    watchDir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);

                    while (true) {
                        WatchKey watckKey = watcher.take();
                        List<WatchEvent<?>> events = watckKey.pollEvents();
                        for (WatchEvent event : events) {
                            // Отслеживается изменение только файла troName
                            if (troName.equals(event.context().toString())){
                                if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                    System.out.println("Modify: " + event.context().toString());
                                    
                                    // Прочитать новые строчки
                                    // BufferedReader.readLine()
                                    try (BufferedReader bufferedReader = new BufferedReader(new FileReader(quikPath + File.separator + event.context().toString()))) {
                                        String line;
                                        while ((line = bufferedReader.readLine()) != null) {
                                            System.out.println(line);
                                        }
                                    } catch (FileNotFoundException e) {
                                        System.out.println("Error: " + e.toString());
                                    }
                                    
                                    // Вызывать callback только для новых строк
                                    
                                }
                            }
                        }
                        if (! watckKey.reset()) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error: " + e.toString());
                } catch (InterruptedException e) {
                    System.out.println("Interrapted: " + e.toString());
                }
            }
        });
        watcherThread.start();
    }
    
    public void stop()
    {
        watcherThread.interrupt();
    }
    
    public static void main(String args[]) throws IOException
    {
        JavaToQuik javaToQuik = new JavaToQuik("C:\\Program Files (x86)\\QUIK-Junior\\");
        System.in.read();
        javaToQuik.stop();
    }
}
