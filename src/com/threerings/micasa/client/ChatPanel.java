//
// $Id$
//
// Vilya library - tools for developing networked games
// Copyright (C) 2002-2012 Three Rings Design, Inc., All Rights Reserved
// http://code.google.com/p/vilya/
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.threerings.micasa.client;

import java.util.StringTokenizer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.HGroupLayout;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.swing.event.AncestorAdapter;

import com.threerings.util.Name;

import com.threerings.crowd.chat.client.ChatDisplay;
import com.threerings.crowd.chat.data.ChatCodes;
import com.threerings.crowd.chat.data.ChatMessage;
import com.threerings.crowd.chat.data.SystemMessage;
import com.threerings.crowd.chat.data.UserMessage;
import com.threerings.crowd.client.OccupantObserver;
import com.threerings.crowd.client.PlaceView;
import com.threerings.crowd.data.OccupantInfo;
import com.threerings.crowd.data.PlaceObject;
import com.threerings.crowd.util.CrowdContext;

import static com.threerings.micasa.Log.log;

public class ChatPanel extends JPanel
    implements ActionListener, ChatDisplay, OccupantObserver, PlaceView
{
    public ChatPanel (CrowdContext ctx)
    {
        this(ctx, true);
    }

    public ChatPanel (CrowdContext ctx, boolean showHintLabel)
    {
        // keep this around for later
        _ctx = ctx;

        // register ourselves with the chat director
        _ctx.getChatDirector().addChatDisplay(this);

        // register as an occupant observer
        _ctx.getOccupantDirector().addOccupantObserver(this);

        GroupLayout gl = new VGroupLayout(GroupLayout.STRETCH);
        gl.setOffAxisPolicy(GroupLayout.STRETCH);
        setLayout(gl);

        // create our scrolling chat text display
        _text = new JTextPane();
        _text.setEditable(false);
        add(new JScrollPane(_text));

        // create our styles and add those to the text pane
        createStyles(_text);

        // add a label for the text entry stuff
        if (showHintLabel) {
            add(new JLabel("Type here to chat:"), GroupLayout.FIXED);
        }

        // create a horizontal group for the text entry bar
        gl = new HGroupLayout(GroupLayout.STRETCH, GroupLayout.STRETCH, 5, GroupLayout.LEFT);
        JPanel epanel = new JPanel(gl);
        epanel.add(_entry = new JTextField());
        _entry.setActionCommand("send");
        _entry.addActionListener(this);
        _entry.setEnabled(false);

        _send = new JButton("Chat!");
        _send.setEnabled(false);
        _send.addActionListener(this);
        _send.setActionCommand("send");
        epanel.add(_send, GroupLayout.FIXED);
        add(epanel, GroupLayout.FIXED);

        // listen to ancestor events to request focus when added
        addAncestorListener(new AncestorAdapter() {
            @Override
            public void ancestorAdded (AncestorEvent e) {
                if (_focus) {
                    _entry.requestFocusInWindow();
                }
            }
        });
    }

    /**
     * For applications where the chat box has extremely limited space,
     * the send button can be removed to leave more space for the text
     * input box.
     */
    public void removeSendButton ()
    {
        if (_send.isVisible()) {
            // _send.getParent().remove(_send);
            _send.setVisible(false);
        }
    }

    /**
     * Sets whether the chat box text entry field requests the keyboard
     * focus when the panel receives {@link
     * AncestorListener#ancestorAdded} or {@link PlaceView#willEnterPlace}
     * events.
     */
    public void setRequestFocus (boolean focus)
    {
        _focus = focus;
    }

    protected void createStyles (JTextPane text)
    {
        StyleContext sctx = StyleContext.getDefaultStyleContext();
        Style defstyle = sctx.getStyle(StyleContext.DEFAULT_STYLE);

        _nameStyle = text.addStyle("name", defstyle);
        StyleConstants.setForeground(_nameStyle, Color.blue);

        _msgStyle = text.addStyle("msg", defstyle);
        StyleConstants.setForeground(_msgStyle, Color.black);

        _errStyle = text.addStyle("err", defstyle);
        StyleConstants.setForeground(_errStyle, Color.red);

        _noticeStyle = text.addStyle("notice", defstyle);
        StyleConstants.setForeground(_noticeStyle, Color.magenta);
    }

    // documentation inherited
    public void actionPerformed (ActionEvent e)
    {
        String cmd = e.getActionCommand();
        if (cmd.equals("send")) {
            sendText();

        } else {
            System.out.println("Unknown action event: " + cmd);
        }
    }

    // documentation inherited
    public void occupantEntered (OccupantInfo info)
    {
        displayOccupantMessage("*** " + info.username + " entered.");
    }

    // documentation inherited
    public void occupantLeft (OccupantInfo info)
    {
        displayOccupantMessage("*** " + info.username + " left.");
    }

    // documentation inherited
    public void occupantUpdated (OccupantInfo oinfo, OccupantInfo info)
    {
    }

    protected void displayOccupantMessage (String message)
    {
        append(message + "\n", _noticeStyle);
    }

    protected void sendText ()
    {
        String text = _entry.getText();

        // if the message to send begins with /tell then parse it and
        // generate a tell request rather than a speak request
        if (text.startsWith("/tell")) {
            StringTokenizer tok = new StringTokenizer(text);
            // there should be at least three tokens: '/tell target word'
            if (tok.countTokens() < 3) {
                displayError("Usage: /tell username message");
                return;
            }

            // skip the /tell and grab the username
            tok.nextToken();
            String username = tok.nextToken();

            // now strip off everything up to the username to get the
            // message
            int uidx = text.indexOf(username);
            String message = text.substring(uidx + username.length()).trim();

            // request to send this text as a tell message
            _ctx.getChatDirector().requestTell(new Name(username), message, null);

        } else if (text.startsWith("/clear")) {
            // clear the chat box
            _ctx.getChatDirector().clearDisplays();

        } else {
            // request to send this text as a chat message
            _ctx.getChatDirector().requestSpeak(null, text, ChatCodes.DEFAULT_MODE);
        }

        // clear out the input because we sent a request
        _entry.setText("");
    }

    // documentation inherited from interface ChatDisplay
    public void clear ()
    {
        _text.setText("");
    }

    // documentation inherited from interface ChatDisplay
    public boolean displayMessage (ChatMessage message, boolean displayed)
    {
        if (message instanceof UserMessage) {
            UserMessage msg = (UserMessage) message;
            if (msg.localtype == ChatCodes.USER_CHAT_TYPE) {
                append("[" + msg.speaker + " whispers] ", _nameStyle);
                append(msg.message + "\n", _msgStyle);
            } else {
                append("<" + msg.speaker + "> ", _nameStyle);
                append(msg.message + "\n", _msgStyle);
            }
            return true;

        } else if (message instanceof SystemMessage) {
            append(message.message + "\n", _noticeStyle);
            return true;

        } else {
            log.warning("Received unknown message type [message=" +
                        message + "].");
            return false;
        }
    }

    protected void displayError (String message)
    {
        append(message + "\n", _errStyle);
    }

    /**
     * Append the specified text in the specified style.
     */
    protected void append (String text, Style style)
    {
        Document doc = _text.getDocument();
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException ble) {
            log.warning("Unable to insert text!? [error=" + ble + "].");
        }
    }

    public void willEnterPlace (PlaceObject place)
    {
        // enable our chat input elements since we're now somewhere that
        // we can chat
        _entry.setEnabled(true);
        _send.setEnabled(true);
        if (_focus) {
            _entry.requestFocusInWindow();
        }
    }

    // documentation inherited
    public void didLeavePlace (PlaceObject place)
    {
        // nothing doing
    }

    @Override
    public Dimension getPreferredSize ()
    {
        Dimension size = super.getPreferredSize();
        // always prefer a sensible but not overly large width. this also
        // prevents us from inheriting a foolishly large preferred width
        // from the JTextPane which sometimes decides it wants to be as
        // wide as its widest line of text rather than wrap that line of
        // text.
        size.width = PREFERRED_WIDTH;
        return size;
    }

    protected CrowdContext _ctx;

    protected boolean _focus = true;

    protected JComboBox _roombox;
    protected JTextPane _text;
    protected JButton _send;
    protected JTextField _entry;

    protected Style _nameStyle;
    protected Style _msgStyle;
    protected Style _errStyle;
    protected Style _noticeStyle;

    /** A width that isn't so skinny that the text is teeny. */
    protected static final int PREFERRED_WIDTH = 200;
}
