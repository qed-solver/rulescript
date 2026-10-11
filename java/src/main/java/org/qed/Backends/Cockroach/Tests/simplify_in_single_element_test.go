package rulescript_test

import (
	"testing"
)

func TestSimplifyInSingleElement(t *testing.T) {
	testSingleElementMembership(t, "SimplifyInSingleElement", false)
}

func TestSQLSimplifyInSingleElement(t *testing.T) {
	expectSQLFires(t, "SimplifyInSingleElement", "SELECT a,b FROM (VALUES (1, 2), (3, 4)) AS v(a,b) WHERE a IN (b)")
}
