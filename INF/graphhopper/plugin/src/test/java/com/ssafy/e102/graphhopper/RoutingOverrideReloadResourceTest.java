package com.ssafy.e102.graphhopper;

public final class RoutingOverrideReloadResourceTest {

	public static void main(String[] args) {
		adminReloadPropagatesSchemaMismatch();
	}

	static void adminReloadPropagatesSchemaMismatch() {
		RoutingOverrideReloadResource resource = new RoutingOverrideReloadResource(new FailingStrictReloadStore());

		try {
			resource.reload();
			throw new AssertionError("admin reload must fail when overlay schema is not ready");
		} catch (IllegalStateException exception) {
			if (!exception.getMessage().contains("schema")) {
				throw new AssertionError("schema mismatch should be visible to admin reload", exception);
			}
		}
	}

	private static final class FailingStrictReloadStore extends RoutingSegmentOverrideStore {

		@Override
		public void reloadStrict() {
			throw new IllegalStateException("schema mismatch");
		}
	}
}
