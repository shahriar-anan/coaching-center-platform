import 'package:flutter_test/flutter_test.dart';
import 'package:mobile/video/youtube_video_id.dart';

void main() {
  test('parses common YouTube URL forms', () {
    expect(youtubeVideoId('https://www.youtube.com/watch?v=dQw4w9WgXcQ'), 'dQw4w9WgXcQ');
    expect(youtubeVideoId('https://youtu.be/oHg5SJYRHA0'), 'oHg5SJYRHA0');
    expect(youtubeVideoId('https://www.youtube.com/embed/9bZkp7q19f0'), '9bZkp7q19f0');
    expect(youtubeVideoId('9bZkp7q19f0'), '9bZkp7q19f0');
    expect(youtubeVideoId('https://example.com/video'), isNull);
  });
}
