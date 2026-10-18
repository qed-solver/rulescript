package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldIsNull(t *testing.T) {
	rule := loadRule(t, "FoldIsNull")
	e := newEnv(t)
	s, sc := e.scan("t", 3)
	before := e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.f.ConstructNull(types.Int), memo.NullSingleton)))
	e.expectRewrite(rule, before, e.f.ConstructSelect(s, e.filters(e.f.ConstructTrue())))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIs(e.v(sc[1]), memo.NullSingleton))))
	e.expectNoMatch(rule, e.f.ConstructSelect(s, e.filters(e.f.ConstructIsNot(e.f.ConstructNull(types.Int), memo.NullSingleton))))
}
