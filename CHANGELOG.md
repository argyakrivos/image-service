# Change log

## 0.2.0 ([#2](https://git.mobcastdev.com/Marvin/image-processor/pull/2) 2015-01-20 10:31:04)

Initial implementation of Image Processor

### New features

- Retrieving a message of the right type
- Reading and storing image via the StorageService
- Image resizing done using code from ResourceService
- Sending an image metadata message to Marvin exchange (to be handled by magrathea)

**Note**: The image transformation bits were extracted from the ResourceService. There is potential to make a common library such that both services can use, but it is not that easy, given the needs of both projects and the specific nature of the ResourceService. For the purposes of the current Sprint, the common library will likely to happen on a different iteration.

## 0.1.0 ([#1](https://git.mobcastdev.com/Marvin/image-processor/pull/1) 2015-01-19 16:48:43)

Bootstrapping message-driven app

### New feature

- Bootstrapped application

