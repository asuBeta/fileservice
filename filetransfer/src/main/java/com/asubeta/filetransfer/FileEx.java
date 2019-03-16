package com.asubeta.filetransfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;

/**
 * @author asubetao@github.com
 * @since 2018/11/15 16:07
 */
public class FileEx {

    private static Logger logger = LoggerFactory.getLogger(FileEx.class);
    private BufferedReader bufferedReader = null;
    private BufferedWriter bufferedWriter = null;
    private DataOutputStream dataOutputStream = null;
    private DataInputStream dataInputStream = null;
    private File file = null;
    private char readOrWrite;
    private static String filePath = System.getProperty("java.io.tmpdir");

    public FileEx() {
        readOrWrite = 'r';
    }

    /**
     * @param mode 0-read,1-write
     */
    public FileEx(char mode) {
        readOrWrite = mode;
    }

    public int openFile(String filename, String path) {
        return openFile(path + File.separator + filename, 1);
    }

    public int openFile(String filename) {
        return openFile(filePath + File.separator + filename, 1);
    }

    private int openFile(String filename, int mode) {
        if (mode == 0) {
            // use default directory
            filename = filePath + File.separator + filename;
        }
        logger.info("file directory->[{}]", filename);

        try {
            if (readOrWrite == 'r') {
                bufferedReader = null;
                file = new File(filename);
                bufferedReader = new BufferedReader(new FileReader(file));
            } else if (readOrWrite == 'w') {
                bufferedWriter = null;
                file = new File(filename);
                bufferedWriter = new BufferedWriter(new FileWriter(file));
            } else if (readOrWrite == 'a') {
                bufferedWriter = null;
            }

            // read or write on stream
            else if (readOrWrite == 'R') {
                dataInputStream = null;
                file = new File(filename);
                dataInputStream = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            } else if (readOrWrite == 'W') {
                dataOutputStream = null;
                file = new File(filename);
                dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            } else {
                return -2;
            }
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
            return -1;
        }

        return 0;
    }

    public void closeFile() {
        try {
            if (readOrWrite == 'r') {
                bufferedReader.close();
                bufferedReader = null;
            } else if (readOrWrite == 'R') {
                dataInputStream.close();
                dataInputStream = null;
            } else if (readOrWrite == 'W') {
                dataOutputStream.flush();
                dataOutputStream.close();
                dataOutputStream = null;
            } else {
                bufferedWriter.flush();
                bufferedWriter.close();
                bufferedWriter = null;
            }
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
        }
    }

    public String readLine() {
        String s = null;

        if (bufferedReader == null) {
            System.out.println("file not opened");
            return null;
        }

        try {
            s = bufferedReader.readLine();
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
        }

        return s;
    }

    /**
     * write one line into file
     *
     * @param str string
     */
    public void writeLine(String str) {
        try {
            bufferedWriter.write(str + "\n");
            bufferedWriter.flush();
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
        }
    }

    /**
     * write buffer into file
     *
     * @param buf bytes buffer
     */
    public void writeBytes(byte[] buf) {
        try {
            dataOutputStream.write(buf);
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
        }
    }

    /**
     * read bytes from file
     *
     * @param buf bytes buffer
     * @return number of read byte
     */
    public int readBytes(byte[] buf) {
        int i = 0;

        try {
            if (dataInputStream != null) {
                i = dataInputStream.read(buf);
            } else {
                return -1;
            }
        } catch (IOException io) {
            return 0;
        }

        return i;
    }

    public int getFileLength() {
        return (int) file.length();
    }

    public String[] readFileToArray(String filename) {
        String sFile = filePath + File.separator + filename;
        if (openFile(sFile) != 0) {
            String[] s = new String[1];
            s[0] = "open file failed!";
            return s;
        }

        ArrayList al = new ArrayList();
        while (true) {
            String s = readLine();
            if (s == null) {
                break;
            }

            al.add(s);
        }

        String[] s;
        if (al.size() > 0) {
            s = new String[al.size()];
            for (int i = 0; i < al.size(); i++) {
                s[i] = (String) al.get(i);
            }
        } else {
            s = new String[1];
            s[0] = "file is empty!";
        }

        for (int i = 0; i < s.length; i++) {
            System.out.println(s[i]);
        }
        closeFile();

        return s;
    }
}
