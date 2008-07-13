package de.sciss.meloncillo.session;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.roydesign.mac.MRJAdapter;

import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuRoot;
import de.sciss.gui.ProgressComponent;
import de.sciss.io.IOUtil;
import de.sciss.meloncillo.Main;
import de.sciss.meloncillo.gui.MainFrame;
import de.sciss.meloncillo.io.XMLRepresentation;
import de.sciss.util.Flag;

public abstract class DocumentFrame
extends AppWindow
{
	protected final Session	doc;
	private final	ActionSave		actionSave;
	private final	ActionSaveAs	actionSaveAs;
	
	protected DocumentFrame( final Session doc )
	{
		super( REGULAR );
		
		this.doc	= doc;

		final BasicApplication	app	= (BasicApplication) AbstractApplication.getApplication();
		final MenuRoot			mr	= app.getMenuBarRoot();
		
		actionSave		= new ActionSave();
		actionSaveAs	= new ActionSaveAs();
		
		mr.putMimic( "file.clearSession", this, new ActionClearSession() );
		mr.putMimic( "file.save", this, actionSave );
		mr.putMimic( "file.saveAs", this, actionSaveAs );
		mr.putMimic( "edit.undo", this, doc.getUndoManager().getUndoAction() );
		mr.putMimic( "edit.redo", this, doc.getUndoManager().getRedoAction() );
		mr.putMimic( "edit.cut", this, getCutAction() );
		mr.putMimic( "edit.copy", this, getCopyAction() );
		mr.putMimic( "edit.paste", this, getPasteAction() );
		mr.putMimic( "edit.clear", this, getDeleteAction() );
		mr.putMimic( "edit.selectAll", this, getSelectAllAction() );
		
		Listener winListener = new AbstractWindow.Adapter() {
// EEE
//			public void windowClosing( AbstractWindow.Event e ) {
//				actionClose.perform();
//			}

			public void windowActivated( AbstractWindow.Event e )
			{
				// need to check 'disposed' to avoid runtime exception in doc handler if document was just closed
// EEE
//				if( !disposed ) {
					app.getDocumentHandler().setActiveDocument( DocumentFrame.this, doc );
					((BasicWindowHandler) app.getWindowHandler()).setMenuBarBorrower( DocumentFrame.this );
//				}
			}
		};
		this.addListener( winListener );
	}
	
	protected abstract Action getCutAction();
	protected abstract Action getCopyAction();
	protected abstract Action getPasteAction();
	protected abstract Action getDeleteAction();
	protected abstract Action getSelectAllAction();

	protected static String getResourceString( String key )
	{
		return AbstractApplication.getApplication().getResourceString( key );
	}
	
	protected void documentClosed()
	{
		// nada...
//		disposed = true;	// important to avoid "too late window messages" to be processed; fucking swing doesn't kill them despite listener being removed
//		this.removeListener( winListener );
//		actionShowWindow.dispose();
//		app.getDocumentHandler().removeDocument( this, doc );	// invokes doc.dispose() and hence this.dispose()
	}

	/*
	 *  Checks if there are unsaved changes to
	 *  the session. If so, displays a confirmation
	 *  dialog. Invokes Save/Save As depending
	 *  on user selection. IF the doc was not dirty,
	 *	or if &quot;Cancel&quot; or
	 *	&quot;Don't save&quot; was chosen, the
	 *	method returns <code>null</code> and the
	 *	<code>confirmed</code> flag reflects whether
	 *	the document should be closed. If a saving
	 *	process should be started, that process is
	 *	returned. Note that the <code>ProcessingThread</code>
	 *	in this case has not yet been started, as to
	 *	allow interested objects to install a listener
	 *	first. So it's their job to call the <code>start</code>
	 *	method!
	 *
	 *  @param  actionName		name of the action that
	 *							threatens the session
	 *	@param	confirmed		a flag that will be set to <code>true</code> if
	 *							the doc is allowed to be closed
	 *							(doc was not dirty or user chose &quot;Don't save&quot;),
	 *							otherwise <code>false</code> (save process
	 *							initiated or user chose &quot;Cancel&quot;).
	 *  @return					a saving process yet to be started or <code>null</code>
	 *							if the doc needn't/shouldn't be saved
	 *
	 *	@see	de.sciss.eisenkraut.util.ProcessingThread#start
	 */
	public ProcessingThread confirmUnsaved( String actionName, Flag confirmed )
	{
		if( !doc.isDirty() ) {
			confirmed.set( true );
			return null;
		}
		
		final Object[]			options	= { getResourceString( "buttonSave" ),
											getResourceString( "buttonCancel" ),
											getResourceString( "buttonDontSave" )};
		final int				choice;
//		final AudioFileDescr	displayAFD	= doc.getDisplayDescr();
		final JOptionPane		op;
		final JDialog 			d;
		final JRootPane			rp;
		final Flag				dont		= new Flag( false );
//		AudioFileDescr[]		afds		= doc.getDescr();
		File					f			= doc.getFile();
		String					name		= doc.getName();
		
		if( name == null ) name = getResourceString( "frameUntitled" );
		
//		choice = JOptionPane.showOptionDialog( getWindow(), name + " :\n" + getResourceString( "optionDlgUnsaved" ),
//											   actionName, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
//											   options, options[1] );
		op = new JOptionPane( name + " :\n" + getResourceString( "optionDlgUnsaved" ),
		                      JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null,
		                      options, options[ 1 ]);
//		d = op.createDialog( getWindow(), actionName );
		d = op.createDialog( null, actionName );
		rp = d.getRootPane();
		if( rp != null ) {
			rp.getInputMap( JComponent.WHEN_IN_FOCUSED_WINDOW ).put(
			  KeyStroke.getKeyStroke( KeyEvent.VK_D, BasicMenuFactory.MENU_SHORTCUT ), "dont" );
			rp.getActionMap().put( "dont", new AbstractAction() {
				public void actionPerformed( ActionEvent e )
				{
					dont.set(  true );
					d.dispose();
				}
			});
		}
		BasicWindowHandler.showDialog( d );
//		d.setVisible( true );
		if( dont.isSet() ) {
			choice = 2;
		} else {
			final Object value = op.getValue();
			if( (value == null) || (value == options[ 1 ])) {
				choice = 1;
			} else if( value == options[ 0 ]) {
				choice = 0;
			} else if( value == options[ 2 ]) {
				choice = 2;
			} else {
				choice = -1;	// throws assertion error in switch block
			}
		}

		switch( choice ) {
		case JOptionPane.CLOSED_OPTION:
		case 1:	// cancel
			confirmed.set( false );
			return null;
			
		case 2:	// don't save
			confirmed.set( true );
			return null;
			
		case 0:
			confirmed.set( false );
			final boolean writeProtected;
			if( f != null ) {
				writeProtected = !f.canWrite() || ((f.getParentFile() != null) && !f.getParentFile().canWrite());
			} else {
				writeProtected = false;
			}
			if( (f == null) || writeProtected ) {
//				afds = actionSaveAs.query( afds );
				f = actionSaveAs.queryFile();
			}
			if( f != null ) {
				return actionSave.initiate( f );
			}
			return null;
			
		default:
			assert false : choice;
			return null;
		}
	}

	private boolean confirmCancel( String actionName )
	{
		if( doc.checkProcess( 50 )) {
			return true;
		}
		
		final int				choice;
//		final AudioFileDescr	displayAFD	= doc.getDisplayDescr();
		String					name	= doc.getName();
		
		if( name == null ) name = getResourceString( "frameUntitled" );
		
		final JOptionPane op = new JOptionPane( name + " :\n" + getResourceString( "optionDlgProcessing" ) +
		                                        "\n(" + doc.getProcessName() + ")?", JOptionPane.WARNING_MESSAGE,
		                                        JOptionPane.YES_NO_OPTION );
//		choice = JOptionPane.showConfirmDialog( getWindow(), name + " :\n" + getResourceString( "optionDlgProcessing" ) +
//		                                        "\n(" + doc.getProcessName() + ")?",
//											    actionName, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE );
//		choice = BasicWindowHandler.showDialog( op, getWindow(), actionName );
		choice = BasicWindowHandler.showDialog( op, null, actionName );
		
		switch( choice ) {
		case JOptionPane.CLOSED_OPTION:
		case JOptionPane.NO_OPTION:
			return false;
			
		case JOptionPane.YES_OPTION:	// abort
			doc.cancelProcess( true );
			return true;
			
		default:
			assert false : choice;
			return false;
		}
	}

	public ProcessingThread closeDocument( String name, boolean force, Flag wasClosed )
	{
		doc.getTransport().stop();
		if( !force ) {
			if( !confirmCancel( name )) {
				wasClosed.set( false );
				return null;
			}
			final ProcessingThread pt = confirmUnsaved( name, wasClosed );
			if( pt != null ) {
				pt.addListener( new ProcessingThread.Listener() {
					public void processStarted( ProcessingThread.Event e ) { /* ignored */ }
					public void processStopped( ProcessingThread.Event e )
					{
						if( e.isDone() ) {
							documentClosed();
						}
					}
				});
				return pt;
			}
		}
		if( wasClosed.isSet() ) {
			documentClosed();
		}
		return null;
	}

	// action for the Clear-Session menu item
	private class ActionClearSession
	extends MenuAction
	{
		private ActionClearSession()
		{
			super();
		}
		
		/*
		 *  Clears the document. If the session was
		 *  modified, the user is asked to confirm the clear
		 *  action. If the transport is running, it will be
		 *  stopped. Undo history is purged.
		 *
		 *  @synchronization	waitExclusive on DOOR_ALL
		 */
		public void actionPerformed( ActionEvent e )
		{
			perform();
		}
		
		private void perform()
		{
			final Flag				confirmed	= new Flag( false );
			final ProcessingThread	pt			= confirmUnsaved( getValue( NAME ).toString(), confirmed );
			
			if( pt == null ) {
				if( !confirmed.isSet() ) return;
				clear();
			} else {
				pt.addListener( new ProcessingThread.Listener() {
					public void processStarted( ProcessingThread.Event e ) {} 
					public void processStopped( ProcessingThread.Event e ) {
						if( e.getProcessingThread().getReturnCode() == ProgressComponent.DONE ) {
							clear();
						}
					} 
				});
			}
		}
		
		private void clear()
		{
			final MainFrame mf = (MainFrame) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN );

			doc.getTransport().stop();
			try {
				doc.bird.waitExclusive( Session.DOOR_ALL );
				doc.getUndoManager().discardAllEdits();
				doc.clear();
				doc.setDirty( false );
				mf.updateTitle();
				mf.clearLog();
			}
			finally {
				doc.bird.releaseExclusive( Session.DOOR_ALL );
			}
		}
	}

	// action for the Save-Session menu item
	private class ActionSave
	extends MenuAction
	implements ProcessingThread.Client
	{
		private ActionSave()
		{
		}

		/**
		 *  Saves a document. If the file
		 *  wasn't saved before, a file chooser
		 *  is shown before.
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f	= doc.getFile(); // (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
		
			if( f == null ) {
				f = actionSaveAs.queryFile();
			}
			if( f != null ) initiate( f ).start();
		}
		
		/**
		 *  Save the session to the given file.
		 *  Transport is stopped before, if it was running.
		 *  On success, undo history is purged and
		 *  <code>setModified</code> and <code>updateTitle</code>
		 *  are called, and the file is added to
		 *  the Open-Recent menu.
		 *
		 *  @param  docFile		the file denoting
		 *						the session's name. note that
		 *						<code>Session</code> will create
		 *						a folder of this name and store the actual
		 *						session data in a file of the same name
		 *						plus .XML suffix inside this folder
		 *  @synchronization	this method is to be called in the event thread
		 */
		protected ProcessingThread initiate( File docFile )
		{
			final Main root = (Main) AbstractApplication.getApplication();
			doc.getTransport().stop();
			final ProcessingThread pt;
//			return new ProcessingThread( this, root, root, doc, getValue( NAME ).toString(), docFile, Session.DOOR_ALL );
			pt = new ProcessingThread( this, root, getValue( NAME ).toString() );
			pt.putClientArg( "doc", doc );
			pt.putClientArg( "file", docFile );
//			pt.start();
			return pt;
		}

		public int processRun( ProcessingThread context )
		throws IOException
		{
			org.w3c.dom.Document			domDoc;
			DocumentBuilderFactory			builderFactory;
			DocumentBuilder					builder;
			TransformerFactory				transformerFactory;
			Transformer						transformer;
			Element							childNode;
			final File						f		= (File) context.getClientArg( "file" );
			final File						dir		= f.getParentFile();
			File							tempDir	= null;
			boolean							success = false;
			final Map						options	= new HashMap();
			final Application				app		= AbstractApplication.getApplication();
		
			builderFactory		= DocumentBuilderFactory.newInstance();
			builderFactory.setValidating( true );
			transformerFactory  = TransformerFactory.newInstance();

			context.setProgression( -1f );

			try {
				builder		= builderFactory.newDocumentBuilder();
				transformer = transformerFactory.newTransformer();
				builder.setEntityResolver( doc );
				domDoc		= builder.newDocument();
				childNode   = domDoc.createElement( Session.XML_ROOT );
				domDoc.appendChild( childNode );
				options.put( XMLRepresentation.KEY_BASEPATH, dir );
//				doc.getMap().putValue( this, Session.MAP_KEY_PATH, f );
//				doc.setName( f.getName() );
				
				if( dir.exists() ) {
//System.err.println( "dir exists "+dir.getAbsolutePath() );
					tempDir = new File( dir.getAbsolutePath() + ".tmp" );
					if( tempDir.exists() ) {
//System.err.println( "temp dir exists "+tempDir.getAbsolutePath() );
						IOUtil.deleteAll( tempDir );
					}
					if( !dir.renameTo( tempDir )) {
						throw new IOException( tempDir.getAbsolutePath() + " : " +
											   IOUtil.getResourceString( "errMakeDir" ));
					}
				}
				if( !dir.mkdirs() ) {
//System.err.println( "mkdir failed : "+dir.getAbsolutePath() );
					throw new IOException( dir.getAbsolutePath() + " : " +
										   IOUtil.getResourceString( "errMakeDir" ));
				}
				
				doc.toXML( domDoc, childNode, options );
				context.setProgression( -1f );
				transformer.setOutputProperty( OutputKeys.DOCTYPE_SYSTEM, Session.ICHNOGRAM_DTD );
				transformer.transform( new DOMSource( domDoc ), new StreamResult( f ));
				MRJAdapter.setFileCreatorAndType( f, app.getMacOSCreator(), Session.MACOS_FILE_TYPE );
				
				doc.getUndoManager().discardAllEdits();

				if( tempDir != null && tempDir.exists() ) {
					IOUtil.deleteAll( tempDir );
				}

				context.setProgression( 1.0f );
				success = true;
			}
			catch( ParserConfigurationException e1 ) {
				context.setException( e1 );
			}
			catch( TransformerConfigurationException e2 ) {
				context.setException( e2 );
			}
			catch( TransformerException e3 ) {
				context.setException( e3 );
			}
			catch( IOException e4 ) {
				context.setException( e4 );
			}
			catch( DOMException e5 ) {
				context.setException( e5 );
			}

			return success ? DONE : FAILED;
		} // run

		public void processCancel( ProcessingThread context ) {}

		public void processFinished( ProcessingThread context )
		{
			final BasicApplication	app	= (BasicApplication) AbstractApplication.getApplication();
			final MainFrame 		mf	= (MainFrame) app.getComponent( Main.COMP_MAIN );
			final File				f	= (File) context.getClientArg( "file" );

			if( context.getReturnCode() == DONE ) {
				doc.setFile( f );
				app.getMenuFactory().addRecent( f );
				doc.setDirty( false );
			} else {
				File tempDir = new File( f.getParentFile().getAbsolutePath() + ".tmp" );
				if( tempDir.exists() ) {
					JOptionPane.showMessageDialog( mf.getWindow(),
						AbstractApplication.getApplication().getResourceString( "warnOldSessionDir" )+ " :\n"+
						tempDir.getAbsolutePath(), getValue( Action.NAME ).toString(),
						JOptionPane.WARNING_MESSAGE );
				}
			}
			mf.updateTitle();
		}
	}
	
	// action for the Save-Session-As menu item
	private class ActionSaveAs
	extends MenuAction
	{
		private ActionSaveAs()
		{
		}

		/*
		 *  Query a file name from the user and save the document
		 */
		public void actionPerformed( ActionEvent e )
		{
			File f = queryFile();
			if( f != null ) actionSave.initiate( f ).start();
		}
		
		/**
		 *  Open a file chooser so the user
		 *  can select a new output file for the session.
		 *
		 *  @return the chosen <coded>File</code> or <code>null</code>
		 *			if the dialog was cancelled.
		 */
		protected File queryFile()
		{
			FileDialog  fDlg;
			String		strFile, strDir;
			File		f;
			int			i;
//			Frame		frame = (Frame) AbstractApplication.getApplication().getComponent( Main.COMP_MAIN );
			final Frame	frame	= new Frame();

			fDlg	= new FileDialog( frame,
				AbstractApplication.getApplication().getResourceString( "fileDlgSave" ),
				FileDialog.SAVE );
			frame.dispose();
			f	= doc.getFile(); // (File) doc.getMap().getValue( Session.MAP_KEY_PATH );
			if( f != null ) f = f.getParentFile();	// use session folder instead of XML file
			if( f != null ) {
				strDir  = f.getParent();
				strFile = f.getName();
				if( strDir != null ) fDlg.setDirectory( strDir );
				fDlg.setFile( strFile );
			}
			fDlg.show();
			strDir	= fDlg.getDirectory();
			strFile	= fDlg.getFile();
			
			if( strFile == null ) return null;   // means the dialog was cancelled

			i = strFile.lastIndexOf( "." );
			strFile = i > 0 ? strFile.substring( 0, i ) : strFile;
			f = new File( new File( strDir, strFile ), strFile + Session.FILE_EXTENSION );
			return f;
		}
	}
}