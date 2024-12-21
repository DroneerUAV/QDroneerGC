#include "SkydroidLink.h"
#include <QJniEnvironment>
#include <QJniObject>
#include "AndroidInterface.h"
#include <QTimer>
#include "QGCMAVLink.h"
#include "MAVLinkProtocol.h"

static SkydroidLink* _skydroidLink = nullptr;

static void jniFlightControlDataRecv(JNIEnv *envA, jobject thizA, jbyteArray dataA, jint len){
    Q_UNUSED(thizA)
    jbyte *bytesL = envA->GetByteArrayElements(dataA, nullptr);
    //(reinterpret_cast<WKGCSJniRecvByteLink*>(userDataA))->recvBytes(reinterpret_cast< char*>(bytesL),len);
    //qDebug()<<"==========jniFlightControlDataRecv"<<QByteArray::fromRawData(reinterpret_cast< char*>(bytesL),len).toHex(' ');
    QByteArray ba =QByteArray::fromRawData(reinterpret_cast< char*>(bytesL),len);
    if(_skydroidLink)
        _skydroidLink->recvFlightControlData(ba);
    envA->ReleaseByteArrayElements(dataA, bytesL, JNI_ABORT);

}

SkydroidLink::SkydroidLink(SharedLinkConfigurationPtr &config, QObject *parent)
    : LinkInterface(config, parent)
{
    _skydroidLink = this;
    QTimer *timer = new QTimer(this);
    connect(timer,&QTimer::timeout,this,[this](){
        if(!_recvData){
            mavlink_message_t message{};
            (void) mavlink_msg_heartbeat_pack_chan(
                MAVLinkProtocol::instance()->getSystemId(),
                MAVLinkProtocol::instance()->getComponentId(),
                this->mavlinkChannel(),
                &message,
                MAV_TYPE_GCS,
                MAV_AUTOPILOT_INVALID,
                MAV_MODE_MANUAL_ARMED,
                0,
                MAV_STATE_ACTIVE
                );

            uint8_t buffer[MAVLINK_MAX_PACKET_LEN];
            const uint16_t len = mavlink_msg_to_send_buffer(buffer, &message);
            _writeBytes(QByteArray::fromRawData((char*)buffer,len));
        }
    });
    timer->start(50);


    _dataStatusTimer.setSingleShot(true);
    _dataStatusTimer.setInterval(3000);
    connect(&_dataStatusTimer,&QTimer::timeout,this,[this](){
        _recvData = false;
    });
    connect(this,&SkydroidLink::sigDataRecv,this,[this](){
        _dataStatusTimer.start();
    });
}

void SkydroidLink::setNativeMethods()
{


    const JNINativeMethod javaMethods[] {
        //{"nativeInit", "()Z", reinterpret_cast<void *>(jniInit)}
        {"jniFlightControlDataRecv", "([BI)V",                     reinterpret_cast<void *>(jniFlightControlDataRecv)},
    };

    QJniEnvironment jniEnv;
    (void) jniEnv.checkAndClearExceptions();

    jclass objectClass = jniEnv->FindClass(AndroidInterface::kJniQGCActivityClassName);
    if (!objectClass) {
        (void) jniEnv.checkAndClearExceptions();
        return;
    }

    const jint val = jniEnv->RegisterNatives(objectClass, javaMethods, sizeof(javaMethods) / sizeof(javaMethods[0]));
    if (val < 0) {
        (void) jniEnv.checkAndClearExceptions();
        return;
    }

    (void) jniEnv.checkAndClearExceptions();

    return;
}

void SkydroidLink::recvFlightControlData(QByteArray &ba)
{
    //qDebug()<<"recvFlightControlData"<<ba.toHex(' ');
    //_recvData = true;
    //emit sigDataRecv();
    emit sigDataRecv();
    emit bytesReceived(this, ba);
}

void SkydroidLink::setDataRecv()
{
   _recvData = true;
}


void SkydroidLink::disconnect()
{

}

bool SkydroidLink::_connect()
{
    return true;
}

void SkydroidLink::_writeBytes(const QByteArray &data)
{
    QJniEnvironment env;
    (void) env.checkAndClearExceptions();
    jbyteArray array = env->NewByteArray(data.length());
    env->SetByteArrayRegion(array,0,data.length(),(jbyte*)(data.data()));
    const jboolean result = QJniObject::callStaticMethod<jboolean>(
        AndroidInterface::kJniQGCActivityClassName,
        "writeSkydroidData",
        "([B)Z",
        array);
    (void) env.checkAndClearExceptions();

}
