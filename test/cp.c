#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

#define BUFSIZE 1024

char buf[BUFSIZE];

int main(int argc, char** argv)
{
  int src, dst, amount;

//  if (argc!=3) {
//    printf("Usage: cp <src> <dst>\n");
//    return 1;
//  }

  src = open(argv[0]);
  if (src==-1) {
    printf("Unable to open %s\n", argv[0]);
    return 1;
  }

  creat(argv[1]);
  dst = open(argv[1]);
  if (dst==-1) {
    printf("Unable to create %s\n", argv[1]);
    return 1;
  }

  while ((amount = read(src, buf, BUFSIZE))>0) {
    write(dst, buf, amount);
  }

  close(src);
  close(dst);

  return 0;
}
