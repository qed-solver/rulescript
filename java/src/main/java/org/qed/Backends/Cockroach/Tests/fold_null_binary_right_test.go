package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNullBinaryRight(t *testing.T) {
	rule := loadRule(t, "FoldNullBinaryRight")
	e := newEnv(t)
	s, cols := e.scan("u", 1)
	null := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(cols[0]), null)))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructLt(e.v(cols[0]), e.num(1)))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructGt(e.v(cols[0]), null))))
}

func TestSQLFoldNullBinaryRight(t *testing.T) {
	expectSQLFires(t, "FoldNullBinaryRight", "SELECT x FROM u WHERE x < CAST(NULL AS INT)")
}
