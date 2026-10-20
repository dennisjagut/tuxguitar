#ifndef _VST3_SOCKET_H
#define _VST3_SOCKET_H

#include <cstdint>
#include <vector>

// Blocking TCP connection to TuxGuitar (TGRemoteHost). All values are little endian.
class VST3Socket
{
public:
	VST3Socket ();
	~VST3Socket ();

	bool connect (int port);
	void close ();

	bool read (void* buffer, std::size_t size);
	bool write (const void* buffer, std::size_t size);

	bool readInt (int32_t& value);
	bool readBytes (std::vector<uint8_t>& value);

private:
	std::intptr_t handle;
};

// Builds a response so it can be sent with a single write.
class VST3Packet
{
public:
	void putInt (int32_t value);
	void putBool (bool value);
	void putFloat (float value);
	void putBytes (const std::vector<uint8_t>& value);

	const std::vector<uint8_t>& data () const { return buffer; }

private:
	std::vector<uint8_t> buffer;
};

#endif
