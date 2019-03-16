#include <stdio.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/stat.h>
#include <netdb.h>
#include <unistd.h>
#include <time.h>
#include <setjmp.h>

#define PACKET 256
#define TA_DATA 2
#define TA_ACK 3
#define BFSIZE 512
#define MAX_BUFSIZE 8192
#define min(a, b) ((a) > (b) ? (b) : (a))

#define H_HIGH_ 1
#define H_LOW_ 0
#define H_LEN_ 4
#define DEFAULT_FILE_PATH "/tmp"

int file_transfer(char *pf);
int sputl(long lbuf, char *sbuf);
int host_byte_order();
int h_sgetl(char *sbuf);
void h_sputl(int lbuf, char *sbuf);
int read_sock(int sock, char *buf);
int net_send(int sock, char *buf, int length);
int net_recv(int sock, char *buf, int length);

/* file send or receive */
int file_transfer(char *pf)
{
    int fdsr, k;
    long len;
    char c, lbuf[BFSIZE + 10], fn[256];
    struct stat stat;

    len = 0;
    memset(lbuf, 0x00, sizeof(lbuf));
    memset(fn, 0x00, sizeof(fn));
    strcpy(fn, pf);
    if (fn[strlen(fn) - 1] != '/')
    {
        fn[strlen(fn)] = '/';
    }

    /* confirm connection */
    /* TA_DATA + length + file(sd/rc) + filename */
    if (read_sock(0, lbuf) < 0)
    {
        return -1;
    }
    /* file name */
    memcpy(fn + strlen(fn), lbuf + 6, strlen(lbuf + 6));

    strcpy(pf, fn);
    if (strncmp(lbuf + 4, "sd", 2) == 0)
    {
        if ((fdsr = open(fn, O_WRONLY | O_CREAT | O_TRUNC, 0644)) < 0)
        {
            write(0, "er", 2);
            return -1;
        }
        else
        {
            write(0, "ok", 2);
        }

        do
        {
            if ((k = read(0, &c, 1)) == -1)
            {
                close(fdsr);
                return -1;
            }
            if (k != 1 || c != TA_DATA)
            {
                c = TA_DATA;
                write(0, &c, 1);
                continue;
            }
            memset(lbuf, 0, BFSIZE);
            if ((k = read(0, lbuf, sizeof(int))) == -1)
            {
                close(fdsr);
                return -1;
            }
            if (k != sizeof(int))
            {
                c = TA_DATA;
                write(0, &c, 1);
                continue;
            }
            len = h_sgetl(lbuf);
            k = net_recv(0, lbuf, min(BFSIZE, len));
            if (k < min(BFSIZE, len))
            {
                c = TA_DATA;
                write(0, &c, 1);
                continue;
            }
            c = TA_ACK;
            write(0, &c, 1);
            write(fdsr, lbuf, k);
        } while (len > BFSIZE);

        close(fdsr);
        return 0;
    }
    if (strncmp(lbuf + 4, "rc", 2) == 0)
    {
        if ((fdsr = open(fn, O_RDONLY)) < 0)
        {
            write(0, "er", 2);
            return (-1);
        }
        write(0, "ok", 2);
        fstat(fdsr, &stat);
        len = (int)stat.st_size;
        while (len)
        {
            k = min(len, BFSIZE);
            lbuf[0] = TA_DATA;
            h_sputl(len, lbuf + 1);
            read(fdsr, lbuf + 1 + sizeof(int), k);
            do
            {
                if (net_send(0, lbuf, 1 + sizeof(int) + k) < 0)
                {
                    close(fdsr);
                    return (-1);
                }
                c = 0;
                read(0, &c, 1);
            } while (c != TA_ACK);
            len -= k;
        }
        close(fdsr);
        return 0;
    }
    return 0;
}

int read_sock(int sock, char *buf)
{
    char c, lbuf[10];
    int ptr, k, len;

    len = 0;
    memset(lbuf, 0, sizeof(lbuf));
    ptr = 0;
    do
    {
        k = read(sock, &c, 1);
        if (k != 1 || c != TA_DATA)
        {
            return -1;
        }
        k = read(sock, lbuf, sizeof(int));
        if (k < sizeof(int))
        {
            return -1;
        }

        len = h_sgetl(lbuf);
        if (len > MAX_BUFSIZE)
        {
            return -1;
        }
        k = net_recv(sock, buf + ptr, min(PACKET, len));
        if (k < min(PACKET, len))
        {
            return -1;
        }
        ptr += k;
    } while (len > PACKET);

    return ptr;
}

int net_send(int sock, char *buf, int length)
{
    int len, nsend = 0;

    while (length)
    {
        if ((len = write(sock, buf + nsend, length)) < 0)
            return -1;
        length -= len;
        nsend += len;
    }
    return nsend;
}

int net_recv(int sock, char *buf, int length)
{
    int len, nrecv = 0;

    while (length)
    {
        len = read(sock, buf + nrecv, length);
        if (len < 0)
        {
            return -1;
        }
        else if (len == 0)
        {
            break;
        }
        length -= len;
        nrecv += len;
    }
    return nrecv;
}

/* get byte orders of host */
int host_byte_order()
{
    union {
        unsigned char c[2];
        unsigned short int i;
    } CharToInt;
    CharToInt.i = 0x12ab;
    if (CharToInt.c[0] == 0x12)
    {
        return H_LOW_;
    }
    else
    {
        return H_HIGH_;
    }
}

/* int to network byte order(4 byte) */
void h_sputl(int lngDataLen, char *lBuf)
{
    union {
        char c[4];
        int l;
    } LongToChar;
    int i;
    LongToChar.l = lngDataLen;
    if ((host_byte_order()) == H_HIGH_)
    {
        for (i = 0; i < H_LEN_; i++)
        {
            lBuf[i] = LongToChar.c[4 - i - 1];
        }
    }
    else
    {
        for (i = 0; i < H_LEN_; i++)
        {
            lBuf[i] = LongToChar.c[i];
        }
    }
    lBuf[4] = 0x00;
    return;
}

/* network byte order(4 byte) to int */
int h_sgetl(char *lBuf)
{
    union {
        char c[4];
        int l;
    } LongToChar;
    int i;
    if ((host_byte_order()) == H_HIGH_)
    {
        for (i = 0; i < H_LEN_; i++)
        {
            LongToChar.c[4 - i - 1] = lBuf[i];
        }
    }
    else
    {
        for (i = 0; i < H_LEN_; i++)
        {
            LongToChar.c[i] = lBuf[i];
        }
    }
    return LongToChar.l;
}

int main(int argc, char *argv[])
{
    int i;
    mode_t mt;
    char f_path[80];

    memset(f_path, 0x00, sizeof(f_path));
    strcpy(f_path, DEFAULT_FILE_PATH);
    if (argc > 2)
    {
        for (i = 1; i < argc; i++)
        {
            if (!strcmp(argv[i], "-u"))
            {
                sscanf(argv[i + 1], "%i", &mt);
                umask(mt);
            }
            /* specialize file path using -d option */
            if (!strcmp(argv[i], "-d"))
            {
                memset(f_path, 0x00, sizeof(f_path));
                strcpy(f_path, argv[i + 1]);
            }
        }
    }
    file_transfer(f_path);
    return 0;
}
