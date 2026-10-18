package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNotNull")
	e := newEnv(t)
	src, sc := e.scan("t", 3)
	before := e.f.ConstructSelect(src, e.filters(e.f.ConstructNot(e.f.ConstructNull(types.Bool))))
	want := e.f.ConstructSelect(src, e.filters(e.f.ConstructNull(types.Bool)))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructNot(e.v(sc[1])))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructNot(e.f.ConstructFalse()))))
}
