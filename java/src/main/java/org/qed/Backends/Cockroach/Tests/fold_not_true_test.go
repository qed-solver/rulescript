package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNotTrue(t *testing.T) {
	rule := loadRule(t, "FoldNotTrue")
	e := newEnv(t)
	src, _ := e.scan("t", 3)
	notTrue := e.f.ConstructNot(e.f.ConstructTrue())
	before := e.f.ConstructSelect(src, e.filters(notTrue))
	want := e.f.ConstructSelect(src, e.filters(e.f.ConstructFalse()))
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructTrue())))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructNot(e.f.ConstructFalse()))))
	e.expectNoMatch(rule, e.f.ConstructSelect(src, e.filters(e.f.ConstructNot(e.f.ConstructNull(types.Bool)))))
}
