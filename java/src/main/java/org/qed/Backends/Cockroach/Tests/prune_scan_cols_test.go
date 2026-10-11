package rulescript_test

import (
	"strings"
	"testing"
)

func TestPruneScanCols(t *testing.T) {
	testPruneScanCols(t, "PruneScanCols")
}

func TestSQLPruneScanCols(t *testing.T) {
	sql := "SELECT a + b FROM (VALUES (1, 2, 3)) AS v(a, b, c)"
	before, _ := planFor(t, sql, nil)
	if !strings.Contains(before, "project") || !strings.Contains(before, "values") {
		t.Fatalf("expected a project over a three-column values relation:\n%s", before)
	}
	got, fired := planFor(t, sql, nil, "PruneScanCols")
	if strings.Join(fired, ",") != "PruneScanCols" {
		t.Fatalf("expected the rule to fire, got %v:\n%s", fired, got)
	}
}
