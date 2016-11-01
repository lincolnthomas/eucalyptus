/*************************************************************************
 * Copyright 2009-2016 Eucalyptus Systems, Inc.
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
package com.eucalyptus.simpleworkflow.stateful;

import com.ctc.wstx.exc.WstxEOFException;
import com.eucalyptus.bootstrap.Bootstrap;
import com.eucalyptus.component.ServiceConfiguration;
import com.eucalyptus.component.Topology;
import com.eucalyptus.util.Consumer;
import com.eucalyptus.util.Consumers;
import com.eucalyptus.util.Exceptions;
import com.eucalyptus.util.async.AsyncRequests;
import com.eucalyptus.util.async.ConnectionException;
import com.eucalyptus.util.concurrent.ListenableFuture;
import com.eucalyptus.ws.WebServicesException;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import org.apache.log4j.Logger;

import java.net.ConnectException;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

/**
 * Created by ethomas on 10/31/16.
 */
public class NotifyClientUtils {

  private static final Logger logger = Logger.getLogger( NotifyClientUtils.class );

  public static void notifyChannel(final ChannelWrapper channelWrapper) {
    final NotifyType notify = new NotifyType( );
    notify.setChannel(channelWrapper.getChannelName());
    try {
      final ListenableFuture<NotifyResponseType> dispatchFuture =
          AsyncRequests.dispatch(Topology.lookup(PolledNotifications.class), notify);
      dispatchFuture.addListener( new Runnable( ) {
        @Override
        public void run() {
          try {
            dispatchFuture.get( );
          } catch ( final InterruptedException e ) {
            logger.info( "Interrupted while sending notification for " + notify.getChannel(), e );
          } catch ( final ExecutionException e ) {
            logger.error( "Error sending notification for " + notify.getChannel( ), e );
            //TODO:STEVE: should retry notification?
          }
        }
      } );
    } catch ( final Exception e ) {
      logger.error( "Error sending notification for " + notify.getChannel( ), e );
    }
  }

  public static void pollChannel(final ChannelWrapper channelWrapper,
                                 final long timeout,
                                 final Consumer<Boolean> resultConsumer) throws Exception {
    final Consumer<Boolean> consumer = Consumers.once(resultConsumer);
    final PollForNotificationType poll = new PollForNotificationType( );
    poll.setChannel(channelWrapper.getChannelName());
    poll.setTimeout( timeout );

    if ( Bootstrap.isShuttingDown() ) {
      delayedPollFailure( 1000L, consumer );
      return;
    }

    final ServiceConfiguration polledNotificationsConfiguration;
    try {
      polledNotificationsConfiguration = Topology.lookup( PolledNotifications.class );
    } catch ( final NoSuchElementException e ){
      delayedPollFailure( 5000L, consumer );
      return;
    }

    final ListenableFuture<PollForNotificationResponseType> dispatchFuture =
        AsyncRequests.dispatch( polledNotificationsConfiguration, poll );
    dispatchFuture.addListener( new Runnable( ) {
      @Override
      public void run( ) {
        try {
          final PollForNotificationResponseType response = dispatchFuture.get( );
          consumer.accept(Objects.firstNonNull(response.getNotified(), false));
        } catch ( final InterruptedException e ) {
          logger.info( "Interrupted while polling for task " + poll.getChannel( ), e );
        } catch ( final ExecutionException e ) {
          if ( Bootstrap.isShuttingDown( ) ) {
            logger.info( "Error polling for task " + poll.getChannel( ) + ": " + Exceptions.getCauseMessage(e) );
          } else {
            handleExecutionExceptionForPolling(e, poll);
          }
        } catch ( final Exception e ) {
          logger.error( "Error polling for task " + poll.getChannel( ), e );
        } finally {
          consumer.accept( false );
        }
      }
    } );
  }

  private static void delayedPollFailure( final long delay,
                                          final Consumer<Boolean> consumer) {
    try {
      Thread.sleep( delay );
    } catch (InterruptedException e1) {
      Thread.currentThread( ).interrupt( );
    } finally {
      consumer.accept( false );
    }
  }

  private static void handleExecutionExceptionForPolling(ExecutionException e, PollForNotificationType poll) {
    Throwable cause = Throwables.getRootCause(e);
    // The following errors occur when the CLC is down or rebooting.
    // com.eucalyptus.ws.WebServicesException: Failed to marshall response:
    // com.eucalyptus.util.async.ConnectionException: Channel was closed before the response was received.:PollForNotificationType
    //java.net.ConnectException: Connection refused:
    // com.ctc.wstx.exc.WstxEOFException: Unexpected EOF in prolog
    // At this point, we just wait a couple of seconds to allow the CLC to reboot.  It will probably take more than a couple of seconds,
    // but this way we will also slow the rate of log error accrual, as otherwise this method is called again immediately.
    if (cause instanceof WebServicesException || cause instanceof ConnectionException || cause instanceof ConnectException || cause instanceof WstxEOFException) {
      logger.info("Error polling for task " + poll.getChannel() + ", CLC likely down.  Will sleep for 5 seconds");
      logger.info(cause.getClass() + ":" + cause.getMessage());
      try {
        Thread.sleep(5000L);
      } catch (InterruptedException e1) {
        logger.info("Interrupted while polling for task " + poll.getChannel(), e1);
      }
    } else {
      logger.error( "Error polling for task " + poll.getChannel( ), e );
    }
  }

  public interface ChannelWrapper {
    public String getChannelName();
  }
}
