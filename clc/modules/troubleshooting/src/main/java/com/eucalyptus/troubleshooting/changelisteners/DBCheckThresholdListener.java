/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
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
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 ************************************************************************/

package com.eucalyptus.troubleshooting.changelisteners;


import com.eucalyptus.configurable.ConfigurableProperty;
import com.eucalyptus.configurable.ConfigurablePropertyException;
import com.eucalyptus.configurable.PropertyChangeListener;
import com.eucalyptus.troubleshooting.checker.schedule.DBCheckScheduler;

public class DBCheckThresholdListener implements PropertyChangeListener<Object> {
	/**
	 * @see com.eucalyptus.configurable.PropertyChangeListener#fireChange(com.eucalyptus.configurable.ConfigurableProperty,
	 *      java.lang.Object)
	 */
	@Override
	public void fireChange(ConfigurableProperty t, Object newValue)
			throws ConfigurablePropertyException {
		if (newValue == null) {
			throw new ConfigurablePropertyException("Invalid value " + newValue);
		} else if (!(newValue instanceof String)) {
			throw new ConfigurablePropertyException("Invalid value " + newValue);
		} else if (((String) newValue).endsWith("%")) { //percentage
			String percentageStr = ((String) newValue).substring(0, ((String) newValue).length() - 1);
			double percentage = -1.0;
			try {
				percentage = Double.parseDouble(percentageStr);
			} catch (Exception ex) {
				throw new ConfigurablePropertyException("Invalid value " + newValue);
			}
			if (percentage < 0 || percentage > 100) {
				throw new ConfigurablePropertyException("Invalid value " + newValue);
			}
		} else {
			int numConnections = -1;
			try {
				numConnections = Integer.parseInt((String) newValue);
			} catch (Exception ex) {
				throw new ConfigurablePropertyException("Invalid value " + newValue);
			}
			if (numConnections <= 0) {
				throw new ConfigurablePropertyException("Invalid value " + newValue);
			}
		}
		try {
			t.getField().set(null, t.getTypeParser().apply(newValue.toString()));
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
			throw new ConfigurablePropertyException(e1);
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
			throw new ConfigurablePropertyException(e1);
		}
		DBCheckScheduler.resetDBCheck();
	}
}
