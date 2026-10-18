package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestCommuteNullIs(t *testing.T) {
	rule := loadRule(t, "CommuteNullIs")
	e := newEnv(t)
	s, cols := e.scan("t", 3)
	nul := e.f.ConstructNull(types.Int)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(nul, e.v(cols[0]))))
	want := e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.v(cols[0]), nul)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIsNot(nul, e.v(cols[0])))))
	e.expectNoMatch(rule, want)
}
