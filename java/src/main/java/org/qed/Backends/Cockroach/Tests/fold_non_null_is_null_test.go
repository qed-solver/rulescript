package rulescript_test

import (
	"testing"
)

func TestFoldNonNullIsNull(t *testing.T) { testFoldNonNullScalar(t, "FoldNonNullIsNull", false) }

func TestSQLFoldNonNullIsNull(t *testing.T) {
	expectSQLFires(t, "FoldNonNullIsNull", "SELECT x FROM (VALUES (1), (2)) AS v(x) WHERE x IS NULL")
}
