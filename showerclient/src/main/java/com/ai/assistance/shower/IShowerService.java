package com.ai.assistance.shower;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IShowerService extends IInterface {

    int ensureDisplay(int width, int height, int dpi, int bitrateKbps) throws RemoteException;

    void destroyDisplay(int displayId) throws RemoteException;

    void launchApp(String packageName, int displayId) throws RemoteException;

    void tap(int displayId, float x, float y) throws RemoteException;

    void swipe(int displayId, float x1, float y1, float x2, float y2, long durationMs) throws RemoteException;

    void touchDown(int displayId, float x, float y) throws RemoteException;

    void touchMove(int displayId, float x, float y) throws RemoteException;

    void touchUp(int displayId, float x, float y) throws RemoteException;

    void injectTouchEvent(
            int displayId,
            int action,
            float x,
            float y,
            long downTime,
            long eventTime,
            float pressure,
            float size,
            int metaState,
            float xPrecision,
            float yPrecision,
            int deviceId,
            int edgeFlags
    ) throws RemoteException;

    void injectKey(int displayId, int keyCode) throws RemoteException;

    void injectKeyWithMeta(int displayId, int keyCode, int metaState) throws RemoteException;

    byte[] requestScreenshot(int displayId) throws RemoteException;

    void setVideoSink(int displayId, IBinder sink) throws RemoteException;

    void registerInputClient(IBinder client) throws RemoteException;

    void unregisterInputClient(IBinder client) throws RemoteException;

    abstract class Stub extends Binder implements IShowerService {

        private static final String DESCRIPTOR = "com.ai.assistance.shower.IShowerService";
        static final int TRANSACTION_ensureDisplay = IBinder.FIRST_CALL_TRANSACTION;
        static final int TRANSACTION_destroyDisplay = IBinder.FIRST_CALL_TRANSACTION + 1;
        static final int TRANSACTION_launchApp = IBinder.FIRST_CALL_TRANSACTION + 2;
        static final int TRANSACTION_tap = IBinder.FIRST_CALL_TRANSACTION + 3;
        static final int TRANSACTION_swipe = IBinder.FIRST_CALL_TRANSACTION + 4;
        static final int TRANSACTION_touchDown = IBinder.FIRST_CALL_TRANSACTION + 5;
        static final int TRANSACTION_touchMove = IBinder.FIRST_CALL_TRANSACTION + 6;
        static final int TRANSACTION_touchUp = IBinder.FIRST_CALL_TRANSACTION + 7;
        static final int TRANSACTION_injectKey = IBinder.FIRST_CALL_TRANSACTION + 8;
        static final int TRANSACTION_requestScreenshot = IBinder.FIRST_CALL_TRANSACTION + 9;
        static final int TRANSACTION_injectKeyWithMeta = IBinder.FIRST_CALL_TRANSACTION + 10;
        static final int TRANSACTION_setVideoSink = IBinder.FIRST_CALL_TRANSACTION + 11;
        static final int TRANSACTION_injectTouchEvent = IBinder.FIRST_CALL_TRANSACTION + 12;
        static final int TRANSACTION_registerInputClient = IBinder.FIRST_CALL_TRANSACTION + 13;
        static final int TRANSACTION_unregisterInputClient = IBinder.FIRST_CALL_TRANSACTION + 14;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IShowerService asInterface(IBinder obj) {
            if (obj == null) {
                return null;
            }
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin instanceof IShowerService) {
                return (IShowerService) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_ensureDisplay: {
                    data.enforceInterface(DESCRIPTOR);
                    int width = data.readInt();
                    int height = data.readInt();
                    int dpi = data.readInt();
                    int bitrate = data.readInt();
                    int _result = ensureDisplay(width, height, dpi, bitrate);
                    reply.writeNoException();
                    reply.writeInt(_result);
                    return true;
                }
                case TRANSACTION_destroyDisplay: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    destroyDisplay(displayId);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_launchApp: {
                    data.enforceInterface(DESCRIPTOR);
                    String pkg = data.readString();
                    int displayId = data.readInt();
                    launchApp(pkg, displayId);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_tap: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    float x = data.readFloat();
                    float y = data.readFloat();
                    tap(displayId, x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_swipe: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    float x1 = data.readFloat();
                    float y1 = data.readFloat();
                    float x2 = data.readFloat();
                    float y2 = data.readFloat();
                    long duration = data.readLong();
                    swipe(displayId, x1, y1, x2, y2, duration);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_touchDown: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    float x = data.readFloat();
                    float y = data.readFloat();
                    touchDown(displayId, x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_touchMove: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    float x = data.readFloat();
                    float y = data.readFloat();
                    touchMove(displayId, x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_touchUp: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    float x = data.readFloat();
                    float y = data.readFloat();
                    touchUp(displayId, x, y);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_injectKey: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    int keyCode = data.readInt();
                    injectKey(displayId, keyCode);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_requestScreenshot: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    byte[] result = requestScreenshot(displayId);
                    reply.writeNoException();
                    reply.writeByteArray(result);
                    return true;
                }
                case TRANSACTION_injectKeyWithMeta: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    int keyCode = data.readInt();
                    int metaState = data.readInt();
                    injectKeyWithMeta(displayId, keyCode, metaState);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_setVideoSink: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    IBinder sink = data.readStrongBinder();
                    setVideoSink(displayId, sink);
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_injectTouchEvent: {
                    data.enforceInterface(DESCRIPTOR);
                    int displayId = data.readInt();
                    int action = data.readInt();
                    float x = data.readFloat();
                    float y = data.readFloat();
                    long downTime = data.readLong();
                    long eventTime = data.readLong();
                    float pressure = data.readFloat();
                    float size = data.readFloat();
                    int metaState = data.readInt();
                    float xPrecision = data.readFloat();
                    float yPrecision = data.readFloat();
                    int deviceId = data.readInt();
                    int edgeFlags = data.readInt();
                    injectTouchEvent(
                            displayId,
                            action,
                            x,
                            y,
                            downTime,
                            eventTime,
                            pressure,
                            size,
                            metaState,
                            xPrecision,
                            yPrecision,
                            deviceId,
                            edgeFlags
                    );
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_registerInputClient: {
                    data.enforceInterface(DESCRIPTOR);
                    registerInputClient(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                }
                case TRANSACTION_unregisterInputClient: {
                    data.enforceInterface(DESCRIPTOR);
                    unregisterInputClient(data.readStrongBinder());
                    reply.writeNoException();
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static final class Proxy implements IShowerService {

            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public int ensureDisplay(int width, int height, int dpi, int bitrateKbps) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(width);
                    data.writeInt(height);
                    data.writeInt(dpi);
                    data.writeInt(bitrateKbps);
                    remote.transact(TRANSACTION_ensureDisplay, data, reply, 0);
                    reply.readException();
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void destroyDisplay(int displayId) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    remote.transact(TRANSACTION_destroyDisplay, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void launchApp(String packageName, int displayId) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(packageName);
                    data.writeInt(displayId);
                    remote.transact(TRANSACTION_launchApp, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void tap(int displayId, float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_tap, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void swipe(int displayId, float x1, float y1, float x2, float y2, long durationMs) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeFloat(x1);
                    data.writeFloat(y1);
                    data.writeFloat(x2);
                    data.writeFloat(y2);
                    data.writeLong(durationMs);
                    remote.transact(TRANSACTION_swipe, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void touchDown(int displayId, float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_touchDown, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void touchMove(int displayId, float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_touchMove, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void touchUp(int displayId, float x, float y) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    remote.transact(TRANSACTION_touchUp, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void injectTouchEvent(
                    int displayId,
                    int action,
                    float x,
                    float y,
                    long downTime,
                    long eventTime,
                    float pressure,
                    float size,
                    int metaState,
                    float xPrecision,
                    float yPrecision,
                    int deviceId,
                    int edgeFlags
            ) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeInt(action);
                    data.writeFloat(x);
                    data.writeFloat(y);
                    data.writeLong(downTime);
                    data.writeLong(eventTime);
                    data.writeFloat(pressure);
                    data.writeFloat(size);
                    data.writeInt(metaState);
                    data.writeFloat(xPrecision);
                    data.writeFloat(yPrecision);
                    data.writeInt(deviceId);
                    data.writeInt(edgeFlags);
                    remote.transact(TRANSACTION_injectTouchEvent, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void injectKey(int displayId, int keyCode) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeInt(keyCode);
                    remote.transact(TRANSACTION_injectKey, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void injectKeyWithMeta(int displayId, int keyCode, int metaState) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeInt(keyCode);
                    data.writeInt(metaState);
                    remote.transact(TRANSACTION_injectKeyWithMeta, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public byte[] requestScreenshot(int displayId) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    remote.transact(TRANSACTION_requestScreenshot, data, reply, 0);
                    reply.readException();
                    return reply.createByteArray();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void setVideoSink(int displayId, IBinder sink) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(displayId);
                    data.writeStrongBinder(sink);
                    remote.transact(TRANSACTION_setVideoSink, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void registerInputClient(IBinder client) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(client);
                    if (!remote.transact(TRANSACTION_registerInputClient, data, reply, 0)) {
                        throw new RemoteException("Shower server does not support input client leases");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void unregisterInputClient(IBinder client) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(client);
                    if (!remote.transact(TRANSACTION_unregisterInputClient, data, reply, 0)) {
                        throw new RemoteException("Shower server does not support input client leases");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
