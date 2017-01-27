/*************************************************************************
 * (c) Copyright 2016 Hewlett Packard Enterprise Development Company LP
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 ************************************************************************/

// PopulateSnapPoints.groovy script
 
package com.eucalyptus.blockstorage

import java.util.List
import java.util.NoSuchElementException

import org.apache.log4j.Logger

import com.ceph.rbd.Rbd
import com.ceph.rbd.RbdImage

import com.eucalyptus.blockstorage.ceph.CephRbdAdapter
import com.eucalyptus.blockstorage.ceph.CephRbdConnectionManager
import com.eucalyptus.blockstorage.ceph.CephRbdFormatTwoAdapter
import com.eucalyptus.blockstorage.ceph.entities.CephRbdInfo
import com.eucalyptus.blockstorage.ceph.exceptions.EucalyptusCephException
import com.eucalyptus.blockstorage.entities.SnapshotInfo
import com.eucalyptus.blockstorage.entities.SnapshotInfo_
import com.eucalyptus.blockstorage.entities.VolumeInfo
import com.eucalyptus.blockstorage.entities.VolumeToken
import com.eucalyptus.blockstorage.util.StorageProperties
import com.eucalyptus.entities.Entities
import com.eucalyptus.entities.EntityRestriction
import com.eucalyptus.entities.TransactionException
import com.eucalyptus.entities.Transactions
import com.eucalyptus.entities.TransactionResource

import com.google.common.base.Function

Logger LOG = Logger.getLogger(PopulateSnapPoints.class)
StringBuffer output = new StringBuffer()

String LINE_PREFIX = "PopulateSnapPoints: "
String line = LINE_PREFIX + "Starting"
output.append(line + '\n')
LOG.info(line)

Function<Object, List<SnapshotInfo>> getSnapshotList = new Function<Object, List<SnapshotInfo>>() {
  @Override
  public List<SnapshotInfo> apply(Object unused) {
    List<SnapshotInfo> snapshots = Entities.criteriaQuery(SnapshotInfo.class).list()
    return snapshots
  }
}

Function<List<SnapshotInfo>, Boolean> storeUpdatedSnapshots = new Function<List<SnapshotInfo>, Boolean>() {
  @Override
  public Boolean apply(List<SnapshotInfo> snapshotsToUpdate) {
    boolean result = true
    for (SnapshotInfo snapshot : snapshotsToUpdate) {
      if (snapshot.getSnapPointId() != null) {
        SnapshotInfo snapshotFromDb = null
        try {
          snapshotFromDb = Entities.criteriaQuery(Entities.restriction(SnapshotInfo.class).equal(SnapshotInfo_.snapshotId, snapshot.getSnapshotId())).uniqueResult()
          snapshotFromDb.setSnapPointId(snapshot.getSnapPointId())
        } catch (Exception e) {
          line = LINE_PREFIX + "Caught Exception looking up snapshot " + snapshot.getSnapshotId() + " from Eucalyptus database: " + e.getMessage()
          output.append(line + '\n')
          LOG.error(line, e)
          result = false
        }
      }
    }
    return new Boolean(result)
  }
}
  
List<SnapshotInfo> snapshots = null
try {
  snapshots =
      Entities.asTransaction(SnapshotInfo.class, getSnapshotList).apply(null)
} catch (Exception e) {
  line = LINE_PREFIX + "Caught Exception getting snapshot list: " + e.getMessage()
  output.append(line + '\n')
  LOG.error(line, e)
}

ArrayList<SnapshotInfo> snapshotsToUpdate = null
if (snapshots != null && !snapshots.isEmpty()) {
  snapshotsToUpdate = new ArrayList<SnapshotInfo>()
  for (SnapshotInfo snapshot : snapshots) {
    line = LINE_PREFIX + "Snapshot info: \n   snapshot ID: " + snapshot.getSnapshotId() + "\n   snapshot point ID: " + snapshot.getSnapPointId()
    LOG.debug(line)
    if (snapshot.getSnapPointId() == null) {
      line = LINE_PREFIX + "Adding snapshot ID " + snapshot.getSnapshotId() + " to list of snapshots to update"
      LOG.debug(line)
      snapshotsToUpdate.add(snapshot)
    }
  }

  boolean anySnapshotsUpdated = false;

  if (snapshotsToUpdate != null && !snapshotsToUpdate.isEmpty()) {

    CephRbdInfo cephInfo = CephRbdInfo.getStorageInfo()

    CephRbdConnectionManager rbdConnection = null

    String[] volumePoolsArray = cephInfo.getAllVolumePools()
    List volumePools = Arrays.asList(volumePoolsArray)

    for (String pool : volumePools) {
      try {
        rbdConnection = CephRbdConnectionManager.getConnection(cephInfo, pool)
        line = LINE_PREFIX + "Connected to Ceph pool " + pool
        LOG.debug(line)
      } catch (Exception e) {
        line = LINE_PREFIX + "Caught Exception connecting to Ceph pool " + pool + ": " + e.getMessage()
        output.append(line + '\n')
        LOG.error(line, e)
        break
      }
      Rbd rbd = rbdConnection.getRbd()
      String[] imageIdsArray = rbd.list(100000 /*100KB initial buffer size*/)
      List imageIds = Arrays.asList(imageIdsArray)

      for (SnapshotInfo snapshot : snapshotsToUpdate) {
        if (snapshot.getSnapPointId() == null) {
          String volumeId = snapshot.getVolumeId()
          String snapshotId = snapshot.getSnapshotId()
          if (imageIds.contains(volumeId)) {
            String snapPointId = pool + CephRbdInfo.POOL_IMAGE_DELIMITER + volumeId + \
                CephRbdInfo.IMAGE_SNAPSHOT_DELIMITER + CephRbdInfo.SNAPSHOT_FOR_PREFIX + snapshotId
            line = LINE_PREFIX + "Found volume " + volumeId + " for snapshot " + snapshotId + " in pool " + pool + ", storing snapshot point " + snapPointId
            output.append(line + '\n')
            LOG.info(line)
            snapshot.setSnapPointId(snapPointId)
            anySnapshotsUpdated = true
          }
        }
      }  // end for all snapshots to update
      if (rbdConnection != null) {
        rbdConnection.close()
      }
    }  // end for each pool

    if (anySnapshotsUpdated) {
      try {
        Boolean result = Entities.asTransaction(SnapshotInfo.class, storeUpdatedSnapshots).apply(snapshotsToUpdate);
        if (result == null || !result.booleanValue()) {
          line = LINE_PREFIX + "Failure trying to store newly updated snapshot points in " + \
          "Eucalyptus database. Snapshots might not be updated."
          output.append(line + '\n')
          LOG.error(line)
        }
      } catch (Exception e) {
        line = LINE_PREFIX + "Caught Exception trying to store newly updated snapshot points in " + \
        "Eucalyptus database. Snapshots might not be updated: " + e.getMessage()
        output.append(line + '\n')
        LOG.error(line, e)
      }
    }

    for (SnapshotInfo snapshot : snapshotsToUpdate) {
      if (snapshot.getSnapPointId() == null) {
        String snapshotId = snapshot.getSnapshotId()
        line = LINE_PREFIX + "Snapshot " + snapshotId + " not found in any pool, snapshot point not updated"
        output.append(line + '\n')
        LOG.info(line)
      }
    }

  } else {
    line = LINE_PREFIX + "No snapshots required updating"
    output.append(line + '\n')
    LOG.info(line)
  }
} else {
  line = LINE_PREFIX + "No snapshots found in Eucalyptus database"
  output.append(line + '\n')
  LOG.info(line)
}

line = LINE_PREFIX + "Finished, exiting"
output.append(line + '\n')
LOG.info(line)

return output

//EOF
