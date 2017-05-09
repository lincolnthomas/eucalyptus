#!/bin/bash
#
# FOR DIAGNOSTIC USE ONLY, WITH DIRECTION FROM DXC EUCALYPTUS SUPPORT PERSONNEL.
#
# (c) Copyright 2017 Hewlett Packard Enterprise Development Company LP
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see http://www.gnu.org/licenses/.
#
# clean-ceph-deleted.sh
#
# Delete the images on Ceph that have already been deleted
# from the list of Eucalyptus volumes and snapshots. Normally this is
# a background task which executes once per minute.

EUCAJARS=$(ls /usr/share/eucalyptus/*.jar|tr '\n' ':')

JAVAPARAMS="-Xbootclasspath/p://///usr/share/eucalyptus/openjdk-crypto.jar -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+DisableExplicitGC -javaagent:///usr/share/eucalyptus/aspectjweaver-1.8.8.jar -Djava.net.preferIPv4Stack=true -Djava.library.path=///usr/lib/eucalyptus -Djava.awt.headless=true -Deuca.home=// -Deuca.db.home= -Deuca.extra_version= -Deuca.var.dir=///var/lib/eucalyptus -Deuca.state.dir=///var/lib/eucalyptus -Deuca.run.dir=///var/run/eucalyptus -Deuca.lib.dir=///usr/share/eucalyptus -Deuca.libexec.dir=///usr/lib/eucalyptus -Deuca.conf.dir=///etc/eucalyptus/cloud.d -Deuca.log.dir=///var/log/eucalyptus -Deuca.jni.dir=///usr/lib/eucalyptus -Djava.util.prefs.PreferencesFactory=com.eucalyptus.util.NoopPreferencesFactory -Deuca.log.exhaustive.db=FATAL -Deuca.log.exhaustive.cc=FATAL -Deuca.log.exhaustive.user=FATAL -Deuca.log.exhaustive.external=FATAL -Deuca.log.exhaustive=FATAL -Deuca.version=4.4.0 -Deuca.log.level=TRACE -Deuca.log.appender=console-log -Deuca.extra_version=0.25812.3.el7 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=///var/log/eucalyptus/ -Deuca.user=eucalyptus -Xms8000m -Xmx63000m -XX:ErrorFile=/var/log/eucalyptus/hs_err_pid%p.log -Dfile.encoding=UTF-8 -cp"

echo $0 starting CephCleaner
java $JAVAPARAMS $EUCAJARS com.eucalyptus.blockstorage.CephCleaner
echo $0 finished CephCleaner

