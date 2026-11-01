/*
 * Copyright 2012 Kulikov Dmitriy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.io.file;

import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connection;
import javax.microedition.io.ConnectionProvider;
import javax.microedition.io.Connector;
import javax.microedition.util.ContextHolder;

/**
 * Emulated file roots. Both the internal (c:/) and external (e:/) MIDP disks are
 * mapped inside filesDir, so games never read or write public storage.
 */
public class FileSystemRegistry implements ConnectionProvider
{
	public static final String INTERNAL_DISK = "c:/";
	public static final String EXTERNAL_DISK = "e:/";
	
	private static Vector roots = new Vector();
	
	public static Enumeration listRoots()
	{
		synchronized(roots)
		{
			roots.removeAllElements();
			roots.addElement(INTERNAL_DISK);
			roots.addElement(EXTERNAL_DISK);
			
			return roots.elements();
		}
	}
	
	public static File getRootDirectory()
	{
		File dir = new File(ContextHolder.getContext().getFilesDir(), "j2me-storage");
		dir.mkdirs();
		return dir;
	}
	
	public static String getInternalStoragePath()
	{
		File dir = new File(getRootDirectory(), "internal");
		dir.mkdirs();
		return dir.getAbsolutePath();
	}
	
	public static String getExternalStoragePath()
	{
		File dir = new File(getRootDirectory(), "external");
		dir.mkdirs();
		return dir.getAbsolutePath();
	}
	
	public static String midpFileToAndroid(String name)
	{
		if(name.startsWith("/"))
		{
			name = name.substring(1);
		}
		
		if(name.startsWith(INTERNAL_DISK))
		{
			name = getInternalStoragePath() + "/" + name.substring(INTERNAL_DISK.length());
		}
		else if(name.startsWith(EXTERNAL_DISK))
		{
			name = getExternalStoragePath() + "/" + name.substring(EXTERNAL_DISK.length());
		}
		else
		{
			name = getInternalStoragePath() + "/" + name;
		}
		
		return name;
	}
	
	public static String androidFileToMIDP(String name)
	{
		String internal = getInternalStoragePath();
		String external = getExternalStoragePath();
		
		if(name.startsWith(external))
		{
			name = EXTERNAL_DISK + name.substring(external.length());
		}
		else if(name.startsWith(internal))
		{
			name = INTERNAL_DISK + name.substring(internal.length());
		}
		else if(name.startsWith("/"))
		{
			name = INTERNAL_DISK + name.substring(1);
		}
		else
		{
			name = INTERNAL_DISK + name;
		}
		
		return "/" + name;
	}
	
	public Connection open(String name, int mode)
	{
		return new FileConnection(new File(midpFileToAndroid(name)), (mode & Connector.WRITE) != 0 ? FileConnection.READ_WRITE : FileConnection.READ);
	}
}