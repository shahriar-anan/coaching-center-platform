/// Parses a YouTube video id from common URL forms (watch, youtu.be, embed).
String? youtubeVideoId(String url) {
  final value = url.trim();
  if (value.isEmpty) {
    return null;
  }
  final embed = RegExp(r'youtube\.com/embed/([A-Za-z0-9_-]{11})');
  final matchEmbed = embed.firstMatch(value);
  if (matchEmbed != null) {
    return matchEmbed.group(1);
  }
  final shortLink = RegExp(r'youtu\.be/([A-Za-z0-9_-]{11})');
  final matchShort = shortLink.firstMatch(value);
  if (matchShort != null) {
    return matchShort.group(1);
  }
  final watch = RegExp(r'[?&]v=([A-Za-z0-9_-]{11})');
  final matchWatch = watch.firstMatch(value);
  if (matchWatch != null) {
    return matchWatch.group(1);
  }
  if (RegExp(r'^[A-Za-z0-9_-]{11}$').hasMatch(value)) {
    return value;
  }
  return null;
}
