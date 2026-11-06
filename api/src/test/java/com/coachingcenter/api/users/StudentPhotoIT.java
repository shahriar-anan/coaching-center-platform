package com.coachingcenter.api.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.files.LocalFileStorage;

/** A student's optional profile picture (F2): students only, re-encoded, at most 1 MB, and private to the student. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StudentPhotoIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private LocalFileStorage fileStorage;

	@Autowired
	private StudentPhotoService photos;

	@Autowired
	private PlatformTransactionManager transactions;

	@Test
	void studentUploadsReplacesShowsAndRemovesTheirPicture() throws Exception {
		UUID student = seed("STUDENT");
		String token = token(student);

		mockMvc.perform(get("/api/v1/me/photo").header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoUrl").doesNotExist());

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "me.jpg", "image/jpeg", jpeg(640, 480)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/me/photo?v=")));

		mockMvc.perform(get("/api/v1/me/photo").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(header().string("Cache-Control", "private, no-cache"))
			.andExpect(header().string("Content-Type", "image/jpeg"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"));

		String first = storedName(student);
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "me.png", "image/png", png(300, 200)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.startsWith("/api/v1/me/photo?v=")));
		String second = storedName(student);
		assertThat(second).isNotEqualTo(first);
		assertThat(Files.exists(fileStorage.resolve(first))).isFalse();
		assertThat(Files.exists(fileStorage.resolve(second))).isTrue();

		mockMvc.perform(delete("/api/v1/me/photo").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.photoUrl").doesNotExist());
		assertThat(Files.exists(fileStorage.resolve(second))).isFalse();
		mockMvc.perform(get("/api/v1/me/photo").header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
		assertThat(rowCount(student)).isZero();
	}

	@Test
	void staffCannotUseThePhotoRoutes() throws Exception {
		String master = token(seed("MASTER_ADMIN"));
		String system = token(seed("SYSTEM_ADMIN"));
		for (String token : new String[] { master, system }) {
			mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
				.file(new MockMultipartFile("file", "staff.jpg", "image/jpeg", jpeg(320, 240)))
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/me/photo").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
			mockMvc.perform(delete("/api/v1/me/photo").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
			mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.photoUrl").doesNotExist());
		}
	}

	@Test
	void nonImagesAndOtherImageTypesAreRefused() throws Exception {
		String token = token(seed("STUDENT"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "notes.txt", "text/plain", "not a picture".getBytes(StandardCharsets.UTF_8)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "fake.png", "image/png", "not a picture".getBytes(StandardCharsets.UTF_8)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "a.gif", "image/gif", gif(64, 64)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "gif-as-jpeg.jpg", "image/jpeg", gif(64, 64)))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void anUploadOverEightMegabytesIsRefused() throws Exception {
		String token = token(seed("STUDENT"));
		byte[] tooLarge = new byte[(int) StudentPhotoService.MAX_UPLOAD_BYTES + 1];

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "huge.jpg", "image/jpeg", tooLarge))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
	}

	@Test
	void aPictureThatStaysOverOneMegabyteAfterProcessingIsRefusedWithoutAFile() throws Exception {
		UUID student = seed("STUDENT");
		String token = token(student);
		byte[] noisy = noisyJpeg(1600);
		Set<String> before = storedFiles();

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "noise.jpg", "image/jpeg", noisy))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isPayloadTooLarge())
			.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

		assertThat(rowCount(student)).isZero();
		assertThat(storedFiles()).isEqualTo(before);
	}

	@Test
	void theStoredPictureCarriesNoExifData() throws Exception {
		String token = token(seed("STUDENT"));
		byte[] withGps = jpegWithGpsExif();
		assertThat(new String(withGps, StandardCharsets.ISO_8859_1)).contains("Exif");

		mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/me/photo")
			.file(new MockMultipartFile("file", "phone.jpg", "image/jpeg", withGps))
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/me/photo").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString(StandardCharsets.ISO_8859_1);
		assertThat(body).startsWith("ÿØÿ");
		assertThat(body).doesNotContain("Exif");
	}

	@Test
	void aRolledBackUploadLeavesNoFileBehind() throws Exception {
		UUID student = seed("STUDENT");
		byte[] image = png(320, 240);
		MockMultipartFile file = new MockMultipartFile("file", "rolled.png", "image/png", image);
		Set<String> before = storedFiles();

		new TransactionTemplate(transactions).executeWithoutResult(status -> {
			photos.upload(student, file);
			status.setRollbackOnly();
		});

		assertThat(rowCount(student)).isZero();
		assertThat(storedFiles()).isEqualTo(before);
	}

	private Set<String> storedFiles() throws IOException {
		Path root = fileStorage.rootDirectory();
		if (!Files.isDirectory(root)) {
			return Set.of();
		}
		try (Stream<Path> files = Files.list(root)) {
			return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
		}
	}

	private String storedName(UUID student) {
		return new JdbcTemplate(dataSource).queryForObject("select stored_name from student_photos where user_id = ?",
				String.class, student);
	}

	private int rowCount(UUID student) {
		return new JdbcTemplate(dataSource).queryForObject("select count(*) from student_photos where user_id = ?",
				Integer.class, student);
	}

	private static byte[] jpeg(int width, int height) throws IOException {
		return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
	}

	private static byte[] png(int width, int height) throws IOException {
		return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png");
	}

	private static byte[] gif(int width, int height) throws IOException {
		return encode(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED), "gif");
	}

	/** Random pixels compress badly, so a 1600 px square stays well above 1 MB after the re-encode. */
	private static byte[] noisyJpeg(int size) throws IOException {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
		Random random = new Random(7);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				image.setRGB(x, y, random.nextInt(0xFFFFFF));
			}
		}
		return encode(image, "jpg");
	}

	/**
	 * A JPEG with a GPS EXIF block in an APP1 segment straight after the start-of-image marker, as a phone camera writes
	 * one. The block holds a GPSInfo pointer and a GPSLatitudeRef of "N".
	 */
	private static byte[] jpegWithGpsExif() throws IOException {
		byte[] jpeg = jpeg(320, 240);
		byte[] tiff = {
				'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // little-endian header, IFD0 at offset 8
				0x01, 0x00, // IFD0: one entry
				0x25, (byte) 0x88, 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, // GPSInfo, LONG, one value
				0x1A, 0x00, 0x00, 0x00, // the GPS IFD is at offset 26
				0x00, 0x00, 0x00, 0x00, // no next IFD
				0x01, 0x00, // GPS IFD: one entry
				0x01, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00, 0x00, 'N', 0x00, 0x00, 0x00, // GPSLatitudeRef, ASCII "N"
				0x00, 0x00, 0x00, 0x00 // no next IFD
		};
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[] { jpeg[0], jpeg[1] });
		out.writeBytes(new byte[] { (byte) 0xFF, (byte) 0xE1, 0x00, 52 });
		out.writeBytes("Exif".getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(new byte[] { 0x00, 0x00 });
		out.writeBytes(tiff);
		out.writeBytes(Arrays.copyOfRange(jpeg, 2, jpeg.length));
		return out.toByteArray();
	}

	private static byte[] encode(BufferedImage image, String format) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, format, out);
		return out.toByteArray();
	}

	private UUID seed(String role) {
		UUID id = UUID.randomUUID();
		String phone = "+8801" + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		new JdbcTemplate(dataSource).update("""
				INSERT INTO users (id, phone, email, password_hash, role, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now())
				""", id, phone, "student-photo-" + id + "@example.com", passwords.encode("password1"), role);
		if (role.endsWith("ADMIN")) {
			new JdbcTemplate(dataSource).update("INSERT INTO admin_profiles (user_id, full_name) VALUES (?, 'Admin')", id);
		}
		else {
			new JdbcTemplate(dataSource).update("INSERT INTO student_profiles (user_id, full_name, student_code)"
					+ " VALUES (?, 'Student', ?)", id, "PHOTO" + id.toString().substring(0, 8));
		}
		return id;
	}

	private String token(UUID userId) throws Exception {
		String phone = new JdbcTemplate(dataSource).queryForObject("select phone from users where id = ?", String.class,
				userId);
		MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"%s","password":"password1"}
					""".formatted(phone)))
			.andExpect(status().isOk())
			.andReturn();
		String body = result.getResponse().getContentAsString();
		String marker = "\"accessToken\":\"";
		int start = body.indexOf(marker) + marker.length();
		return body.substring(start, body.indexOf('"', start));
	}

}
