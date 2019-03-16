package com.asubeta.filetransfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Java Client for filetr service
 *
 * @author asubetao@github.com
 * @since 2018/11/15 16:05
 */
public class FileTransferClient {

    private static Logger logger = LoggerFactory.getLogger(FileTransferClient.class);
    private static final int TA_DATA = 2;
    private static final int TA_ACK = 3;
    private static final int BUFFER_SIZE = 1024;
    private static final int MAX_FILENAME_LEN = 256;
    private static final int SOCK_TIMEOUT = 60000;
    private static final String COMMAND_SEND = "filesd";
    private static final String COMMAND_RECEIVE = "filerc";

    private DataInputStream in;
    private DataOutputStream out;

    private String ip;
    private String port;

    public FileTransferClient(String ip, String port) {
        this.ip = ip;
        this.port = port;
    }

    /**
     * send file to server
     *
     * @param filename file to be sent
     * @param path     if blank, path is default java tmp directory
     * @return 0-success;
     * 1-cannot open source file;
     * 2-cannot establish connection;
     * 3-cannot create remote file;
     * 4-error on file transfer;
     */
    public int sendFile(String filename, String path) {
        try {
            logger.info("send file to server {}:{}", ip, port);

            FileEx ef = new FileEx('R');
            int ret;
            if (path == null || path.trim().length() == 0) {
                ret = ef.openFile(filename);
            } else {
                ret = ef.openFile(filename, path);
            }
            if (ret != 0) {
                logger.error("open file " + filename + " error!");
                return 1;
            }

            Socket sock = new Socket(ip, Integer.parseInt(port));
            sock.setSoTimeout(SOCK_TIMEOUT);

            out = new DataOutputStream(sock.getOutputStream());
            in = new DataInputStream(sock.getInputStream());

            if (filename.length() > MAX_FILENAME_LEN) {
                return 1;
            }

            String command = COMMAND_SEND + filename;
            byte packageFlag = (byte) 2;
            byte[] pkg = command.getBytes();

            // write to tmp buffer
            ByteArrayOutputStream tmpBuff = new ByteArrayOutputStream();
            DataOutputStream tmpOut = new DataOutputStream(tmpBuff);
            tmpOut.writeByte(packageFlag);
            tmpOut.writeInt(pkg.length);
            tmpOut.write(pkg);
            tmpOut.flush();
            out.write(tmpBuff.toByteArray());
            out.flush();
            tmpOut.close();
            logger.info("FILE TRANSFER CLIENT send command:[{}]", command);

            // receive remote file creating response
            byte[] b = new byte[2];
            in.read(b);
            String confirm = new String(b);
            logger.info("get confirm:[{}]", confirm);
            if (!confirm.equals("ok")) {
                logger.error("send file failed, response is: {}", confirm);
                in.close();
                out.close();
                sock.close();
                return 3;
            }

            // begin sending file
            int fileLength = ef.getFileLength();
            logger.info("begin to send file[{}], total size = [{}]", filename, fileLength);
            while (fileLength > 0) {
                int k = fileLength > BUFFER_SIZE ? BUFFER_SIZE : fileLength;

                // read from file
                byte[] buf = new byte[k];
                ef.readBytes(buf);
                while (true) {
                    out.writeByte(TA_DATA);
                    out.writeInt(fileLength);
                    out.write(buf);
                    out.flush();

                    if (TA_ACK != in.read()) {
                        logger.error("sendfile on road get confirm error!");
                        continue;
                    }
                    break;
                }

                fileLength = fileLength - k;
            }

            logger.info("send file finished.");
            ef.closeFile();
            in.close();
            out.close();
            sock.close();
        } catch (ConnectException e) {
            e.printStackTrace();
            return 2;
        } catch (SocketTimeoutException e) {
            return 4;
        } catch (IOException e) {
            e.printStackTrace();
            return 4;
        }

        return 0;
    }


    /**
     * send file to server
     *
     * @param filename file to be received in default directory
     * @param path     if blank, path is default java tmp directory
     * @return 0-success;
     * 1-cannot open source file;
     * 2-cannot establish connection;
     * 3-cannot create remote file;
     * 4-error on file transfer;
     */
    public int getFile(String filename, String path) {
        try {
            logger.info("get file from server {}:{}", ip, port);

            FileEx ef = new FileEx('W');
            int ret;
            if (path == null || path.trim().length() == 0) {
                ret = ef.openFile(filename);
            } else {
                ret = ef.openFile(filename, path);
            }

            if (ret != 0) {
                logger.error("open file " + filename + "error!");
                return 3;
            }

            Socket sock = new Socket(ip, Integer.parseInt(port));
            sock.setSoTimeout(SOCK_TIMEOUT);

            out = new DataOutputStream(sock.getOutputStream());
            in = new DataInputStream(sock.getInputStream());

            if (filename.length() > MAX_FILENAME_LEN) {
                return 1;
            }

            String command = COMMAND_RECEIVE + filename;
            logger.info("FILE TRANSFER CLIENT get command:[{}]", command);

            byte packageFlag = (byte) 2;
            byte[] pkg = command.getBytes();

            // write to tmp buffer
            java.io.ByteArrayOutputStream tmpBuff = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream tmpOut = new java.io.DataOutputStream(tmpBuff);
            tmpOut.writeByte(packageFlag);
            tmpOut.writeInt(pkg.length);
            tmpOut.write(pkg);
            tmpOut.flush();

            out.write(tmpBuff.toByteArray());
            out.flush();
            tmpOut.close();

            // receive remote file sending response
            byte[] b = new byte[2];
            in.read(b);
            String confirm = new String(b);
            logger.info("get confirm:[{}]", confirm);
            if (!confirm.equals("ok")) {
                logger.error("get file failed, response is: {}", confirm);
                in.close();
                out.close();
                sock.close();
                return 1;
            }

            // begin receiving file
            logger.info("begin to get file[{}]...", filename);
            int totalSize = 0;
            while (true) {
                byte inByte = in.readByte();
                if (TA_DATA != (int) inByte) {
                    logger.info("receive file on road get :" + inByte);
                    out.writeByte(TA_DATA);
                    continue;
                }

                int inLength = in.readInt();
                int k = inLength < BUFFER_SIZE ? inLength : BUFFER_SIZE;
                byte[] buff = new byte[k];
                if (in.read(buff) < k) {
                    out.writeByte(TA_DATA);
                    continue;
                }

                out.writeByte(TA_ACK);
                ef.writeBytes(buff);
                totalSize += buff.length;

                if (inLength < BUFFER_SIZE) {
                    break;
                }
            }

            logger.info("receive file finished, total size = [{}]", totalSize);
            ef.closeFile();
            in.close();
            out.close();
            sock.close();
        } catch (ConnectException e) {
            e.printStackTrace();
            return 2;
        } catch (SocketTimeoutException e) {
            return 4;
        } catch (IOException e) {
            e.printStackTrace();
            return 4;
        }

        return 0;
    }
}
