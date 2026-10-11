package rulescript_test

import (
	"testing"
)

func TestSimplifyNotInSingleElement(t *testing.T) {
	testSingleElementMembership(t, "SimplifyNotInSingleElement", true)
}

func TestSQLSimplifyNotInSingleElement(t *testing.T) {
	expectSQLFires(t, "SimplifyNotInSingleElement", "SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE a NOT IN (b)")
}
