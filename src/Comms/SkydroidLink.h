#ifndef SKYDROIDLINK_H
#define SKYDROIDLINK_H

#include <QObject>

#include "LinkConfiguration.h"
#include "LinkInterface.h"
#include <QTimer>

class SkydroidConfigure : public LinkConfiguration
{
    Q_OBJECT
public:
    SkydroidConfigure (const QString &name, QObject *parent = nullptr): LinkConfiguration(name, parent)
    {}
    LinkType type() const override { return LinkConfiguration::TypeSkydroid; }
    void copyFrom(const LinkConfiguration *source) override{}
    void loadSettings(QSettings &settings, const QString &root) override{}
    void saveSettings(QSettings &settings, const QString &root) override{}
    QString settingsURL() override { return "";}
    QString settingsTitle() override { return "";}
};

class SkydroidLink : public LinkInterface
{
    Q_OBJECT
public:
    explicit SkydroidLink(SharedLinkConfigurationPtr &config, QObject *parent = nullptr);

    bool isConnected() const override{ return true;};
    bool isSecureConnection() override { return true;}

    static void setNativeMethods();

    void recvFlightControlData(QByteArray & ba);

    void setDataRecv();

public slots:
    void disconnect() override;
signals:
    void sigDataRecv();
private:
    bool _connect() override;
    void _writeBytes(const QByteArray &data) override;
    bool _recvData = false;
    QTimer _dataStatusTimer;
};

#endif // SKYDROIDLINK_H
