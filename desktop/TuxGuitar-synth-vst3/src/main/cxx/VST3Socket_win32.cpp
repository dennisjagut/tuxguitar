#include "VST3Socket.h"

#include <cstring>

#include <winsock2.h>
#include <ws2tcpip.h>

VST3Socket::VST3Socket ()
: handle (static_cast<std::intptr_t> (INVALID_SOCKET))
{
	WSADATA data;
	WSAStartup (MAKEWORD (2, 2), &data);
}

VST3Socket::~VST3Socket ()
{
	close ();
	WSACleanup ();
}

bool VST3Socket::connect (int port)
{
	SOCKET s = socket (AF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (s == INVALID_SOCKET)
		return false;

	sockaddr_in address {};
	address.sin_family = AF_INET;
	address.sin_port = htons (static_cast<u_short> (port));
	address.sin_addr.s_addr = htonl (INADDR_LOOPBACK);

	if (::connect (s, reinterpret_cast<sockaddr*> (&address), sizeof (address)) == SOCKET_ERROR)
	{
		closesocket (s);
		return false;
	}

	BOOL noDelay = TRUE;
	setsockopt (s, IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*> (&noDelay), sizeof (noDelay));

	handle = static_cast<std::intptr_t> (s);
	return true;
}

void VST3Socket::close ()
{
	SOCKET s = static_cast<SOCKET> (handle);
	if (s != INVALID_SOCKET)
	{
		shutdown (s, SD_BOTH);
		closesocket (s);
		handle = static_cast<std::intptr_t> (INVALID_SOCKET);
	}
}

bool VST3Socket::read (void* buffer, std::size_t size)
{
	char* bytes = static_cast<char*> (buffer);
	std::size_t offset = 0;
	while (offset < size)
	{
		int count = recv (static_cast<SOCKET> (handle), bytes + offset, static_cast<int> (size - offset), 0);
		if (count <= 0)
			return false;
		offset += static_cast<std::size_t> (count);
	}
	return true;
}

bool VST3Socket::write (const void* buffer, std::size_t size)
{
	const char* bytes = static_cast<const char*> (buffer);
	std::size_t offset = 0;
	while (offset < size)
	{
		int count = send (static_cast<SOCKET> (handle), bytes + offset, static_cast<int> (size - offset), 0);
		if (count <= 0)
			return false;
		offset += static_cast<std::size_t> (count);
	}
	return true;
}

bool VST3Socket::readInt (int32_t& value)
{
	uint8_t bytes[4];
	if (!read (bytes, sizeof (bytes)))
		return false;

	value = static_cast<int32_t> (static_cast<uint32_t> (bytes[0]) | (static_cast<uint32_t> (bytes[1]) << 8) |
	                              (static_cast<uint32_t> (bytes[2]) << 16) | (static_cast<uint32_t> (bytes[3]) << 24));
	return true;
}

bool VST3Socket::readBytes (std::vector<uint8_t>& value)
{
	int32_t length = 0;
	if (!readInt (length) || length < 0)
		return false;

	value.resize (static_cast<std::size_t> (length));
	return (length == 0 || read (value.data (), value.size ()));
}

void VST3Packet::putInt (int32_t value)
{
	uint32_t bits = static_cast<uint32_t> (value);
	buffer.push_back (static_cast<uint8_t> (bits & 0xFF));
	buffer.push_back (static_cast<uint8_t> ((bits >> 8) & 0xFF));
	buffer.push_back (static_cast<uint8_t> ((bits >> 16) & 0xFF));
	buffer.push_back (static_cast<uint8_t> ((bits >> 24) & 0xFF));
}

void VST3Packet::putBool (bool value)
{
	buffer.push_back (value ? 1 : 0);
}

void VST3Packet::putFloat (float value)
{
	int32_t bits = 0;
	std::memcpy (&bits, &value, sizeof (bits));
	putInt (bits);
}

void VST3Packet::putBytes (const std::vector<uint8_t>& value)
{
	putInt (static_cast<int32_t> (value.size ()));
	buffer.insert (buffer.end (), value.begin (), value.end ());
}
