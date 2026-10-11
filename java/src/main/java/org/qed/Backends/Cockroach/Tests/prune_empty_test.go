package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneEmpty(t *testing.T) {
	rule := loadRule(t, "PruneEmpty")
	e := newEnv(t)
	c := e.f.Metadata().AddColumn("c", types.Int)
	input := e.empty(c)
	before := e.f.ConstructSelect(input, e.filters(e.f.ConstructGt(e.v(c), e.num(0))))
	e.expectRewrite(rule, before, input)

	e.expectNoMatch(rule, input)
	scan, sc := e.scan("t", 1)
	e.expectNoMatch(rule, e.f.ConstructSelect(scan, e.filters(e.f.ConstructGt(e.v(sc[0]), e.num(0)))))
	e.expectNoMatch(rule, e.f.ConstructProject(input, nil, opt.MakeColSet(c)))
}
